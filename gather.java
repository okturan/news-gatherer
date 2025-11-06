// File: GdeltDedupe.java
import java.net.http.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class GdeltDedupe {

  static final String GDELT_DOC = "https://api.gdeltproject.org/api/v2/doc/doc";
  static final ZoneId IST = ZoneId.of("Europe/Istanbul");

  static final Set<String> WIRE = Set.of("aa.com.tr","iha.com.tr","dha.com.tr");
  static final Set<String> AGG  = Set.of("ensonhaber.com","haberler.com","sondakika.com","gazeteoku.com");
  static final Set<String> STOP = Set.of("son","dakika","video","galeri","izle","foto","yorum","haber","haberi","güncel","flas","flaş");

  record Article(
      String url, String title, String domain,
      String lang, String sourcecountry,
      ZonedDateTime seen, ZonedDateTime published,
      String sourceType, String titleNorm, Set<String> shingles, String canonUrl
  ) {}

  public static void main(String[] args) throws Exception {
    var articles = fetch("sourcecountry:turkey sourcelang:turkish","2h",200);
    if (articles.isEmpty()) { System.out.println("No articles."); return; }
    var clusters = cluster(articles, 48, 0.80);

    // Print clusters
    var sorted = clusters.stream()
      .sorted((a,b)-> timeOf(a.get(0)).compareTo(timeOf(b.get(0))).reversed())
      .toList();

    int i=1;
    for (var cluster : sorted) {
      int canonIdx = pickCanonical(cluster);
      var canon = cluster.get(canonIdx);
      var ts = timeOf(canon).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
      System.out.println("\n["+ (i++) +"] " + ts + " • CANONICAL ("+canon.sourceType+") " + canon.domain);
      System.out.println("     " + canon.title);
      System.out.println("     " + canon.canonUrl);
      if (cluster.size() > 1) {
        System.out.println("     Members ("+cluster.size()+"):");
        cluster.stream()
          .sorted(Comparator.comparing(GdeltDedupe::timeOf))
          .forEach(m -> {
            String mark = (m==canon) ? "★" : "•";
            System.out.printf("       %s %-11s %-20s %s  %s%n",
                mark, m.sourceType, m.domain,
                timeOf(m).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                truncate(m.title, 90));
          });
      }
    }

    System.out.println("\n=== METRICS ===");
    int total = articles.size();
    int clustersN = clusters.size();
    System.out.println("Articles fetched:   " + total);
    System.out.println("Story clusters:     " + clustersN);
    System.out.printf ("Avg items/cluster:  %.2f%n", (double)total/clustersN);
  }

  static List<Article> fetch(String query, String timespan, int maxrecords) throws Exception {
    var client = HttpClient.newHttpClient();
    var uri = new URI(GDELT_DOC + "?" + URIQuery(query, timespan, maxrecords));
    var resp = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    var root = (ObjectNode) new ObjectMapper().readTree(resp.body());
    var arr  = (ArrayNode)(root.get("articles")!=null ? root.get("articles") : root.get("artlist"));

    List<Article> out = new ArrayList<>();
    if (arr==null) return out;

    for (var n : arr) {
      String url   = txt(n,"url", "urlMobile");
      String title = txt(n,"title","titleMobile");
      if (url==null || title==null) continue;
      String domain = opt(n,"domain", parseDomain(url)).toLowerCase(Locale.ROOT);
      String lang   = opt(n,"language", opt(n,"sourcelang", null));
      String sc     = opt(n,"sourcecountry", null);
      ZonedDateTime seen = parseTime(opt(n,"seendate", opt(n,"date", null)));
      ZonedDateTime pub  = parseTime(opt(n,"publishdate", opt(n,"published", null)));

      String type = sourceType(domain);
      String tnorm = normalizeTitle(title);
      Set<String> sh = shingles(tnorm, 4);
      String canon = canonicalUrl(url);
      out.add(new Article(url, title, domain, lang, sc, seen, pub, type, tnorm, sh, canon));
    }
    return out;
  }

  static String URIQuery(String q, String ts, int max) throws Exception {
    var params = new LinkedHashMap<String,String>();
    params.put("query", q);
    params.put("mode","artlist");
    params.put("format","json");
    params.put("timespan", ts);
    params.put("maxrecords", String.valueOf(max));
    params.put("sort","datedesc");
    var sb = new StringBuilder();
    for (var e : params.entrySet()) {
      if (sb.length()>0) sb.append('&');
      sb.append(URLEncoder.encode(e.getKey(),"UTF-8"))
        .append('=')
        .append(URLEncoder.encode(e.getValue(),"UTF-8"));
    }
    return sb.toString();
  }

  // Clustering (O(n^2) within a 48h window) -----------------------------------
  static List<List<Article>> cluster(List<Article> arts, int windowHours, double thr) {
    var idx = new ArrayList<Integer>();
    for (int i=0;i<arts.size();i++) idx.add(i);
    idx.sort(Comparator.comparing((Integer i)-> arts.get(i).seen).reversed());

    var dsu = new DSU(arts.size());
    var win = Duration.ofHours(windowHours);

    for (int aPos=0; aPos<idx.size(); aPos++) {
      int i = idx.get(aPos);
      var A = arts.get(i);
      for (int j=aPos+1; j<idx.size(); j++) {
        int k = idx.get(j);
        var B = arts.get(k);
        if (Duration.between(B.seen, A.seen).compareTo(win) > 0) break;
        double sim = jaccard(A.shingles, B.shingles);
        if (sim >= thr) dsu.union(i,k);
      }
    }

    Map<Integer,List<Article>> groups = new HashMap<>();
    for (int i=0;i<arts.size();i++) {
      groups.computeIfAbsent(dsu.find(i), _k-> new ArrayList<>()).add(arts.get(i));
    }
    return new ArrayList<>(groups.values());
  }

  static int pickCanonical(List<Article> cluster) {
    Comparator<Article> byTime = Comparator.comparing(GdeltDedupe::timeOf);
    var wires = cluster.stream().filter(a -> a.sourceType.equals("WIRE")).min(byTime);
    if (wires.isPresent()) return cluster.indexOf(wires.get());
    var pubs  = cluster.stream().filter(a -> a.sourceType.equals("PUBLISHER")).min(byTime);
    if (pubs.isPresent())  return cluster.indexOf(pubs.get());
    var aggs  = cluster.stream().filter(a -> a.sourceType.equals("AGGREGATOR")).min(byTime);
    return aggs.map(cluster::indexOf).orElse(0);
  }

  // Helpers -------------------------------------------------------------------
  static ZonedDateTime timeOf(Article a){ return a.published!=null ? a.published : a.seen; }

  static String txt(JsonNode n, String... keys){
    for (var k: keys) { var v = n.get(k); if (v!=null && !v.isNull()) return v.asText(); }
    return null;
  }
  static String opt(JsonNode n, String k, String d){ var v = n.get(k); return v==null||v.isNull() ? d : v.asText(); }

  static ZonedDateTime parseTime(String s){
    if (s==null) return null;
    try {
      // GDELT often uses "yyyy-MM-dd HH:mm:ss"
      if (s.length()==19 && s.charAt(10)==' ') {
        var ldt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return ldt.atZone(IST);
      }
      return ZonedDateTime.parse(s).withZoneSameInstant(IST);
    } catch(Exception e){ return null; }
  }

  static String parseDomain(String u){
    try {
      String h = new URI(u).getHost().toLowerCase(Locale.ROOT);
      return h!=null && h.startsWith("www.") ? h.substring(4) : h;
    } catch(Exception e){ return ""; }
  }

  static String canonicalUrl(String u){
    try {
      var p = new URI(u);
      var q = p.getQuery();
      Map<String,List<String>> kept = new LinkedHashMap<>();
      if (q!=null) for (var kv : q.split("&")) {
        var parts = kv.split("=",2);
        String k = URLDecoder.decode(parts[0],"UTF-8");
        if (k.startsWith("utm_") || Set.of("gclid","fbclid","xtor","trk","ref").contains(k)) continue;
        String v = parts.length>1 ? parts[1] : "";
        kept.computeIfAbsent(k, _k-> new ArrayList<>()).add(v);
      }
      String newQ = kept.isEmpty()? null :
        kept.entrySet().stream()
          .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
          .collect(Collectors.joining("&"));
      var clean = new URI(p.getScheme(), p.getUserInfo(), p.getHost(), p.getPort(), stripSlash(p.getPath()), newQ, null);
      return clean.toString();
    } catch(Exception e){ return u; }
  }
  static String stripSlash(String s){ return (s!=null && s.endsWith("/")) ? s.substring(0,s.length()-1) : s; }

  static String sourceType(String d){
    if (WIRE.contains(d)) return "WIRE";
    if (AGG.contains(d))  return "AGGREGATOR";
    return "PUBLISHER";
  }

  static String normalizeTitle(String t){
    t = t.replace("İ","i").replace("I","ı").toLowerCase(new Locale("tr","TR"));
    t = t.replaceAll("[\\p{Punct}]+"," ").replaceAll("\\s+"," ").trim();
    var toks = Arrays.stream(t.split(" ")).filter(w -> !STOP.contains(w)).toList();
    return String.join(" ", toks);
  }
  static Set<String> shingles(String s, int n){
    s = " " + s + " ";
    var set = new HashSet<String>();
    for (int i=0; i<=Math.max(0, s.length()-n); i++) set.add(s.substring(i, i+n));
    return set;
  }
  static double jaccard(Set<String>a, Set<String>b){
    if (a.isEmpty() || b.isEmpty()) return 0d;
    int inter=0;
    for (var x:a) if (b.contains(x)) inter++;
    int union = a.size() + b.size() - inter;
    return union==0? 0d : (double)inter/union;
  }
  static String truncate(String s, int n){ return (s.length()<=n)? s : s.substring(0,n-1)+"…"; }

  // Tiny DSU
  static class DSU {
    int[] p, r;
    DSU(int n){ p=new int[n]; r=new int[n]; for(int i=0;i<n;i++) p[i]=i; }
    int find(int x){ return p[x]==x? x : (p[x]=find(p[x])); }
    void union(int a, int b){
      int ra=find(a), rb=find(b); if (ra==rb) return;
      if (r[ra]<r[rb]) p[ra]=rb; else if (r[ra]>r[rb]) p[rb]=ra; else { p[rb]=ra; r[ra]++; }
    }
  }
}
