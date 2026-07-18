# Security policy

## Supported version

Security fixes target the current `master` branch. CI artifacts are temporary
build evidence rather than a supported distribution, and historical commits
are not patched in place.

## Reporting a vulnerability

Please use [GitHub's private vulnerability reporting form](https://github.com/okturan/news-gatherer/security/advisories/new)
instead of opening a public issue. Include the affected command, a reduced input
or response, clear reproduction steps, the Java and operating-system versions,
and the security impact.

Relevant reports include:

- injection or unsafe output caused by untrusted GDELT titles, domains, URLs, or
  JSON fields;
- path traversal, unsafe file creation, or unintended overwrite through output
  directory, database, or JSON-output configuration;
- SQLite corruption or a transaction-boundary flaw that can be used to cross a
  data or filesystem boundary;
- a dependency, packaged JAR, or GitHub Actions supply-chain weakness with a
  demonstrated impact.

Missing articles, upstream GDELT inaccuracies, saturated result windows,
canonicalization disagreements, and rate-limit behavior are normal data-quality
or reliability bugs unless they create a security impact. GDELT and fetched
publisher sites are external services; this repository is responsible for how
their untrusted metadata is handled, not for their availability or content.

Use synthetic responses and a disposable database. Do not attach credentials,
private article datasets, or destructive payloads. The maintainer will
coordinate validation, remediation, and disclosure through the private
advisory.
