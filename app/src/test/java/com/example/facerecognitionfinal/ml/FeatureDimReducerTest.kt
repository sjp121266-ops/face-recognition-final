package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class FeatureDimReducerTest {

    @Test
    fun testEmptyState() {
        val reducer = FeatureDimReducer()
        assertTrue(reducer.getPoints().isEmpty())
        assertNull(reducer.getScanPoint())

        // 运行一步仿真不应崩溃
        reducer.stepSimulation()
        assertTrue(reducer.getPoints().isEmpty())
    }

    @Test
    fun testUpdateLibraryAndFeedScan() {
        val reducer = FeatureDimReducer()
        val emb1 = FloatArray(128) { 0.1f }
        val emb2 = FloatArray(128) { 0.5f }

        val profiles = listOf(
            PersonProfile("Alice", mutableListOf(emb1)),
            PersonProfile("Bob", mutableListOf(emb2))
        )

        reducer.updateLibrary(profiles)
        val points = reducer.getPoints()
        assertEquals(2, points.size)
        assertEquals("Alice", points[0].name)
        assertEquals("Bob", points[1].name)
        assertFalse(points[0].isScan)

        // feed scan node
        val scanEmb = FloatArray(128) { 0.12f }
        reducer.feedScan(scanEmb)
        val scanPt = reducer.getScanPoint()
        assertNotNull(scanPt)
        assertEquals("SCAN", scanPt!!.name)
        assertTrue(scanPt.isScan)

        // clear scan node
        reducer.clearScan()
        assertNull(reducer.getScanPoint())
    }

    @Test
    fun testForceDirectedSimulationConvergence() {
        // 创建两个相同人员的不同样本（距离应较近）和一个相异人员样本（距离应较远）
        val reducer = FeatureDimReducer(l2Scale = 50f)
        val alice1 = FloatArray(128) { 0.05f }
        // 和 alice1 非常接近
        val alice2 = FloatArray(128) { 0.06f }
        // 和 alice 组较远
        val bob = FloatArray(128) { 0.5f }

        val profiles = listOf(
            PersonProfile("Alice", mutableListOf(alice1, alice2)),
            PersonProfile("Bob", mutableListOf(bob))
        )

        reducer.updateLibrary(profiles)

        // 迭代仿真 100 步让其物理平衡收敛
        for (i in 0 until 100) {
            reducer.stepSimulation()
        }

        val points = reducer.getPoints()
        assertEquals(3, points.size)

        // 验证重心居中对齐：所有点的 X 和 Y 之和应该接近于 0
        val sumX = points.sumOf { it.x.toDouble() }
        val sumY = points.sumOf { it.y.toDouble() }
        assertEquals(0.0, sumX, 0.1)
        assertEquals(0.0, sumY, 0.1)

        // 验证 Alice 内部的两个粒子物理间距比 Alice 和 Bob 之间的间距要小得多
        val pAlice1 = points.first { it.name == "Alice" && it.x != points.last { it.name == "Alice" }.x }
        val pAlice2 = points.last { it.name == "Alice" }
        val pBob = points.first { it.name == "Bob" }

        val distAliceSelf = dist2D(pAlice1.x, pAlice1.y, pAlice2.x, pAlice2.y)
        val distAliceBob1 = dist2D(pAlice1.x, pAlice1.y, pBob.x, pBob.y)
        val distAliceBob2 = dist2D(pAlice2.x, pAlice2.y, pBob.x, pBob.y)

        assertTrue("Alice内部距离 ($distAliceSelf) 应小于 Alice与Bob距离 ($distAliceBob1)", distAliceSelf < distAliceBob1)
        assertTrue("Alice内部距离 ($distAliceSelf) 应小于 Alice与Bob距离 ($distAliceBob2)", distAliceSelf < distAliceBob2)
    }

    private fun dist2D(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
