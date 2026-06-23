package com.example.facerecognitionfinal.ml

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class VpTreeTest {

    private val random = Random(42) // Fixed seed for reproducibility

    private fun generateRandomVector(dim: Int = 128): FloatArray {
        return FloatArray(dim) { random.nextFloat() }
    }

    @Test
    fun testEmptyTree() {
        val tree = VpTree()
        tree.build(emptyList())
        
        val query = generateRandomVector()
        val result = tree.findNearest(query)
        
        assertNull(result)
        assertEquals(0, tree.size)
        assertEquals(0, tree.lastQueryLinearScans)
        assertEquals(0, tree.lastQueryIndexScans)
        assertEquals(0, tree.lastQuerySavingsPercent)
        assertEquals("未检索", tree.lastQueryRoute)
    }

    @Test
    fun testSingleElementTree() {
        val tree = VpTree()
        val entry = VpTree.Entry("Alice", generateRandomVector())
        tree.build(listOf(entry))
        
        val query = generateRandomVector()
        val result = tree.findNearest(query)
        
        assertNotNull(result)
        assertEquals("Alice", result!!.entry.name)
        assertEquals(1, tree.size)
        assertEquals(1, tree.lastQueryIndexScans)
        assertEquals(0, tree.lastQuerySavingsPercent)
        assertEquals("Alice", tree.lastQueryRoute)
    }

    @Test
    fun testMultipleElementsExactMatch() {
        val tree = VpTree()
        val entries = (1..50).map { i ->
            VpTree.Entry("Person$i", generateRandomVector())
        }
        tree.build(entries)
        
        // Query with exact vectors from the tree
        for (target in entries) {
            val result = tree.findNearest(target.embedding)
            assertNotNull(result)
            assertEquals(target.name, result!!.entry.name)
            assertEquals(0f, result.distance, 1e-6f)
        }
    }

    @Test
    fun testNearestNeighborEquivalentToBruteForce() {
        val tree = VpTree()
        val entries = (1..100).map { i ->
            VpTree.Entry("Person$i", generateRandomVector())
        }
        tree.build(entries)

        // Query with 20 random queries
        for (q in 1..20) {
            val query = generateRandomVector()
            
            // 1. Linear scan (brute-force)
            var bestLinearEntry: VpTree.Entry? = null
            var bestLinearDist = Float.MAX_VALUE
            for (entry in entries) {
                val dist = EmbeddingDistance.l2(query, entry.embedding)
                if (dist < bestLinearDist) {
                    bestLinearDist = dist
                    bestLinearEntry = entry
                }
            }

            // 2. VP-Tree search
            val result = tree.findNearest(query)
            
            assertNotNull(result)
            assertEquals(bestLinearEntry!!.name, result!!.entry.name)
            assertEquals(bestLinearDist, result.distance, 1e-5f)
        }
    }

    @Test
    fun testSearchPruningEfficiency() {
        // Larger dataset to verify pruning works and saves distance comparisons
        val tree = VpTree()
        val entries = (1..200).map { i ->
            VpTree.Entry("Person$i", generateRandomVector(dim = 4))
        }
        tree.build(entries)

        var totalIndexScans = 0
        val queryCount = 50
        for (q in 1..queryCount) {
            val query = generateRandomVector(dim = 4)
            tree.findNearest(query)
            totalIndexScans += tree.lastQueryIndexScans
        }

        val averageIndexScans = totalIndexScans.toFloat() / queryCount
        val bruteForceScans = entries.size.toFloat()
        
        println("VP-Tree Pruning Stats: average nodes inspected = $averageIndexScans / $bruteForceScans")

        // Assert that we inspected significantly fewer nodes on average than brute-force scans (N = 200)
        assertTrue("Average inspected nodes ($averageIndexScans) should be less than brute-force scans ($bruteForceScans)", 
            averageIndexScans < bruteForceScans)
            
        // Savings percent should be reported correctly
        assertTrue(tree.lastQuerySavingsPercent >= 0)
    }

    @Test
    fun testSearchPruningEfficiencyWithClustered128D() {
        val tree = VpTree()
        
        // Create 10 clusters (people), each with 20 face samples (total 200 embeddings) in 128D space
        val centers = (1..10).map { generateRandomVector(dim = 128) }
        val entries = mutableListOf<VpTree.Entry>()
        for (i in 0 until 10) {
            val center = centers[i]
            for (j in 1..20) {
                // Add tiny noise to simulate different photos of the same person
                val embedding = FloatArray(128) { idx ->
                    center[idx] + random.nextFloat() * 0.05f
                }
                entries.add(VpTree.Entry("Person${i}_Sample$j", embedding))
            }
        }
        
        tree.build(entries)
        
        var totalIndexScans = 0
        val queryCount = 50
        for (q in 1..queryCount) {
            // Query with a point close to one of the people's clusters
            val centerIdx = random.nextInt(10)
            val center = centers[centerIdx]
            val query = FloatArray(128) { idx ->
                center[idx] + random.nextFloat() * 0.05f
            }
            tree.findNearest(query)
            totalIndexScans += tree.lastQueryIndexScans
        }

        val averageIndexScans = totalIndexScans.toFloat() / queryCount
        val bruteForceScans = entries.size.toFloat()
        
        println("Clustered 128D Pruning Stats: average nodes inspected = $averageIndexScans / $bruteForceScans")
        
        // Assert that we inspected fewer nodes than brute-force scans (N = 200)
        assertTrue("Clustered 128D: Average inspected nodes ($averageIndexScans) should be less than brute-force scans ($bruteForceScans)", 
            averageIndexScans < bruteForceScans)
    }
}
