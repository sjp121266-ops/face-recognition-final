package com.example.facerecognitionfinal.ml

class VpTree {

    data class Entry(
        val name: String,
        val embedding: FloatArray
    )

    class Node(
        val vp: Entry,
        val threshold: Float,
        val left: Node?,
        val right: Node?
    )

    var root: Node? = null
        private set
    var size: Int = 0
        private set

    // Statistics for the last query
    var lastQueryLinearScans: Int = 0
    var lastQueryIndexScans: Int = 0
    var lastQuerySavingsPercent: Int = 0
    var lastQueryDurationNs: Long = 0L
    var lastQueryRoute: String = "未检索"

    fun build(entries: List<Entry>) {
        size = entries.size
        root = buildTree(entries)
    }

    private fun buildTree(items: List<Entry>): Node? {
        if (items.isEmpty()) return null
        
        val vp = items[0]
        if (items.size == 1) {
            return Node(vp, 0f, null, null)
        }
        
        val list = items.subList(1, items.size)
        val distances = list.map { l2(vp.embedding, it.embedding) }
        
        val sortedDistances = distances.sorted()
        val median = sortedDistances[sortedDistances.size / 2]
        
        val leftItems = mutableListOf<Entry>()
        val rightItems = mutableListOf<Entry>()
        
        for (i in list.indices) {
            if (distances[i] <= median) {
                leftItems.add(list[i])
            } else {
                rightItems.add(list[i])
            }
        }
        
        return Node(
            vp = vp,
            threshold = median,
            left = buildTree(leftItems),
            right = buildTree(rightItems)
        )
    }

    fun findNearest(query: FloatArray): SearchResult? {
        lastQueryLinearScans = size
        lastQueryIndexScans = 0
        lastQuerySavingsPercent = 0
        lastQueryDurationNs = 0L
        lastQueryRoute = "未检索"

        val rootNode = root ?: return null
        val startTime = System.nanoTime()
        
        var bestEntry: Entry? = null
        var bestDist = Float.MAX_VALUE
        var indexScans = 0
        val routeBuilder = StringBuilder()
        
        fun search(node: Node) {
            indexScans++
            val dist = l2(query, node.vp.embedding)
            
            if (dist < bestDist) {
                bestDist = dist
                bestEntry = node.vp
            }
            
            if (routeBuilder.isEmpty()) {
                routeBuilder.append(node.vp.name)
            } else {
                routeBuilder.append(" -> ${node.vp.name}")
            }
            
            if (node.left == null && node.right == null) return
            
            // VP-Tree search and pruning
            if (dist <= node.threshold) {
                if (node.left != null && dist - bestDist <= node.threshold) {
                    search(node.left)
                }
                if (node.right != null && dist + bestDist > node.threshold) {
                    search(node.right)
                }
            } else {
                if (node.right != null && dist + bestDist > node.threshold) {
                    search(node.right)
                }
                if (node.left != null && dist - bestDist <= node.threshold) {
                    search(node.left)
                }
            }
        }
        
        search(rootNode)
        
        val endTime = System.nanoTime()
        
        lastQueryDurationNs = endTime - startTime
        lastQueryIndexScans = indexScans
        lastQueryLinearScans = size
        lastQuerySavingsPercent = if (size > 0) {
            ((size - indexScans).toFloat() / size * 100f).toInt().coerceAtLeast(0)
        } else {
            0
        }
        lastQueryRoute = routeBuilder.toString().trim()
        
        return bestEntry?.let {
            SearchResult(
                entry = it,
                distance = bestDist
            )
        }
    }

    private fun l2(a: FloatArray, b: FloatArray): Float {
        return EmbeddingDistance.l2(a, b)
    }

    data class SearchResult(
        val entry: Entry,
        val distance: Float
    )
}
