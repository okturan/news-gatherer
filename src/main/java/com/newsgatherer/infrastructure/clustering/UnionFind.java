package com.newsgatherer.infrastructure.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disjoint Set Union (Union-Find) data structure with path compression
 * and union by rank optimizations.
 *
 * <p>Used for efficient clustering of articles by similarity.
 * Time complexity: O(α(n)) for both find and union operations,
 * where α is the inverse Ackermann function (effectively constant).
 */
public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private final int size;

    /**
     * Creates a new Union-Find structure with n elements.
     * Initially, each element is in its own set.
     *
     * @param size the number of elements
     */
    public UnionFind(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        this.size = size;
        this.parent = new int[size];
        this.rank = new int[size];

        // Initialize: each element is its own parent
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
    }

    /**
     * Finds the root/representative of the set containing element.
     * Uses path compression for optimization.
     *
     * @param element the element to find (must be 0 <= element < size)
     * @return the root of the set containing element
     * @throws IllegalArgumentException if element is out of bounds
     */
    public int find(int element) {
        if (element < 0 || element >= size) {
            throw new IllegalArgumentException("Element out of bounds: " + element);
        }

        if (parent[element] != element) {
            // Path compression: make element point directly to root
            parent[element] = find(parent[element]);
        }
        return parent[element];
    }

    /**
     * Unions the sets containing elements a and b.
     * Uses union by rank to keep tree height minimal.
     *
     * @param elementA first element
     * @param elementB second element
     * @throws IllegalArgumentException if either element is out of bounds
     */
    public void union(int elementA, int elementB) {
        int rootA = find(elementA);
        int rootB = find(elementB);

        if (rootA == rootB) {
            return; // Already in same set
        }

        // Union by rank: attach smaller tree under larger tree
        if (rank[rootA] < rank[rootB]) {
            parent[rootA] = rootB;
        } else if (rank[rootA] > rank[rootB]) {
            parent[rootB] = rootA;
        } else {
            // Same rank: choose one and increment its rank
            parent[rootB] = rootA;
            rank[rootA]++;
        }
    }

    /**
     * Checks if two elements are in the same set.
     *
     * @param elementA first element
     * @param elementB second element
     * @return true if both elements are in the same set
     */
    public boolean isConnected(int elementA, int elementB) {
        return find(elementA) == find(elementB);
    }

    /**
     * Returns all sets as a map from root to list of elements.
     *
     * @return map of root element to all elements in that set
     */
    public Map<Integer, List<Integer>> getAllSets() {
        Map<Integer, List<Integer>> sets = new HashMap<>();

        for (int i = 0; i < size; i++) {
            int root = find(i);
            sets.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        return sets;
    }

    /**
     * Returns the number of elements in this Union-Find structure.
     *
     * @return the size
     */
    public int getSize() {
        return size;
    }
}
