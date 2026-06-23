package com.example.facerecognitionfinal.ml

import kotlin.math.sqrt
import kotlin.random.Random

class FeatureDimReducer(
    private val kSpring: Float = K_SPRING,
    private val kRepel: Float = K_REPEL,
    private val damping: Float = DAMPING,
    private val l2Scale: Float = L2_SCALE
) {

    data class PointNode(
        val name: String,
        val embedding: FloatArray,
        var x: Float,
        var y: Float,
        var vx: Float = 0f,
        var vy: Float = 0f
    )

    private val nodes = mutableListOf<PointNode>()
    private var scanNode: PointNode? = null

    @Synchronized
    fun updateLibrary(profiles: List<com.example.facerecognitionfinal.data.PersonProfile>) {
        val existingMap = nodes.associateBy { it.embedding }
        nodes.clear()

        for (profile in profiles) {
            for (emb in profile.embeddings) {
                val existing = existingMap[emb]
                if (existing != null) {
                    // 保留已有位置，保证物理过渡平滑，不发生突变
                    nodes.add(PointNode(profile.name, emb, existing.x, existing.y, existing.vx, existing.vy))
                } else {
                    // 随机散开初始化
                    nodes.add(PointNode(
                        profile.name,
                        emb,
                        Random.nextFloat() * 120f - 60f,
                        Random.nextFloat() * 120f - 60f
                    ))
                }
            }
        }
    }

    @Synchronized
    fun feedScan(embedding: FloatArray) {
        val currentScan = scanNode
        if (currentScan != null && currentScan.embedding.contentEquals(embedding)) {
            // 如果是同一个向量，不重置其位置，保持其物理位置连续
        } else {
            scanNode = PointNode(
                "SCAN",
                embedding,
                Random.nextFloat() * 80f - 40f,
                Random.nextFloat() * 80f - 40f
            )
        }
    }

    @Synchronized
    fun clearScan() {
        scanNode = null
    }

    @Synchronized
    fun stepSimulation() {
        if (nodes.isEmpty()) return

        // 1. 计算库粒子之间的相互弹簧力与排斥力
        for (i in 0 until nodes.size) {
            val nodeA = nodes[i]
            var fx = 0f
            var fy = 0f

            for (j in 0 until nodes.size) {
                if (i == j) continue
                val nodeB = nodes[j]

                val dx = nodeB.x - nodeA.x
                val dy = nodeB.y - nodeA.y
                val dist2D = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)

                // 128维 L2 欧氏距离
                val l2Dist = l2Distance(nodeA.embedding, nodeB.embedding)
                val targetDist = l2Dist * l2Scale

                // 弹簧引/力 (胡克定律)
                val force = kSpring * (dist2D - targetDist)

                // 如果是不同人，且距离过近，施加额外的排斥力以更清晰地划分簇群
                val repelForce = if (nodeA.name != nodeB.name && dist2D < REPEL_BOUND) {
                    -kRepel * (REPEL_BOUND - dist2D)
                } else {
                    0f
                }

                val totalForce = force + repelForce
                fx += (dx / dist2D) * totalForce
                fy += (dy / dist2D) * totalForce
            }

            // 更新速度
            nodeA.vx = (nodeA.vx + fx) * damping
            nodeA.vy = (nodeA.vy + fy) * damping
        }

        // 2. 计算当前扫描人脸粒子受库中各个已知粒子的引力
        val scan = scanNode
        if (scan != null) {
            var fx = 0f
            var fy = 0f
            for (node in nodes) {
                val dx = node.x - scan.x
                val dy = node.y - scan.y
                val dist2D = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)

                val l2Dist = l2Distance(scan.embedding, node.embedding)
                val targetDist = l2Dist * l2Scale

                val force = kSpring * (dist2D - targetDist)
                fx += (dx / dist2D) * force
                fy += (dy / dist2D) * force
            }
            scan.vx = (scan.vx + fx) * damping
            scan.vy = (scan.vy + fy) * damping
        }

        // 3. 应用速度，计算总重心位置以做重力对齐
        var sumX = 0f
        var sumY = 0f
        for (node in nodes) {
            node.x += node.vx
            node.y += node.vy
            sumX += node.x
            sumY += node.y
        }
        if (scan != null) {
            scan.x += scan.vx
            scan.y += scan.vy
            sumX += scan.x
            sumY += scan.y
        }

        // 居中平移，使“特征星云”始终漂浮于视图中央，防止溢出
        val count = nodes.size + (if (scan != null) 1 else 0)
        val centerX = sumX / count
        val centerY = sumY / count
        for (node in nodes) {
            node.x -= centerX
            node.y -= centerY
        }
        if (scan != null) {
            scan.x -= centerX
            scan.y -= centerY
        }
    }

    @Synchronized
    fun getPoints(): List<Point2D> {
        return nodes.map { Point2D(it.name, it.x, it.y, false) }
    }

    @Synchronized
    fun getScanPoint(): Point2D? {
        val scan = scanNode ?: return null
        return Point2D(scan.name, scan.x, scan.y, true)
    }

    private fun l2Distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    data class Point2D(
        val name: String,
        val x: Float,
        val y: Float,
        val isScan: Boolean
    )

    companion object {
        const val K_SPRING = 0.05f
        const val K_REPEL = 0.02f
        const val DAMPING = 0.85f
        const val L2_SCALE = 60f // 像素缩放比
        const val REPEL_BOUND = 100f
    }
}
