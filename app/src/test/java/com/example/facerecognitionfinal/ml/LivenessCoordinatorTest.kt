package com.example.facerecognitionfinal.ml

import org.junit.Assert.*
import org.junit.Test

class LivenessCoordinatorTest {

    @Test
    fun testLivenessFlowTransitions() {
        val coordinator = LivenessCoordinator(stepTimeoutMs = 1000L)
        assertEquals(LivenessCoordinator.State.INACTIVE, coordinator.currentState)
        assertFalse(coordinator.isFinished)

        // 启动活体检测
        coordinator.start()
        val firstState = coordinator.currentState
        assertNotEquals(LivenessCoordinator.State.INACTIVE, firstState)
        assertFalse(coordinator.isFinished)
        assertEquals(3, coordinator.totalSteps)
        assertEquals(1, coordinator.currentStepNumber)

        // 模拟给入不匹配的帧（例如没有任何特殊姿势，眼睛睁开）
        // 确保不会跳转动作
        var state = coordinator.feedFrame(
            yawDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProb = 0.9f,
            rightEyeOpenProb = 0.9f
        )
        assertEquals(firstState, state)

        // 根据第一个状态，分步骤去触发
        // 我们这里依次执行直到通过。
        // 因为开始时可能随机到了 BLINK 或 TURN，这里我们需要写兼容的触发逻辑：
        feedUntilVerified(coordinator)
        
        assertEquals(LivenessCoordinator.State.VERIFIED, coordinator.currentState)
        assertTrue(coordinator.isFinished)
    }

    @Test
    fun testLivenessTimeout() {
        // 使用非常短的超时时间，以便测试
        val coordinator = LivenessCoordinator(stepTimeoutMs = 50L)
        coordinator.start()
        
        // 等待超时发生
        Thread.sleep(100L)
        
        val state = coordinator.feedFrame(
            yawDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProb = 0.9f,
            rightEyeOpenProb = 0.9f
        )
        
        assertEquals(LivenessCoordinator.State.TIMEOUT, state)
        assertTrue(coordinator.isFinished)
        assertEquals(0, coordinator.currentStepNumber)
    }

    @Test
    fun testStopLiveness() {
        val coordinator = LivenessCoordinator()
        coordinator.start()
        assertNotEquals(LivenessCoordinator.State.INACTIVE, coordinator.currentState)

        coordinator.stop()
        assertEquals(LivenessCoordinator.State.INACTIVE, coordinator.currentState)
        assertEquals(0, coordinator.currentStepNumber)
        assertEquals(0, coordinator.totalSteps)
    }

    private fun feedUntilVerified(coordinator: LivenessCoordinator) {
        val maxAttempts = 20
        var attempts = 0
        while (!coordinator.isFinished && attempts < maxAttempts) {
            attempts++
            val curState = coordinator.currentState
            when (curState) {
                LivenessCoordinator.State.PROMPT_BLINK -> {
                    // 眨眼序列：
                    // 1. 睁眼 (feedFrame中默认睁眼已经在start中触发或我们刚才发了0.9)
                    coordinator.feedFrame(0f, 0f, 0.9f, 0.9f)
                    // 2. 闭眼
                    coordinator.feedFrame(0f, 0f, 0.05f, 0.05f)
                    // 3. 再睁眼
                    coordinator.feedFrame(0f, 0f, 0.9f, 0.9f)
                }
                LivenessCoordinator.State.PROMPT_TURN_LEFT -> {
                    // 向左偏头 (yaw > 20)
                    coordinator.feedFrame(25f, 0f, 0.9f, 0.9f)
                }
                LivenessCoordinator.State.PROMPT_TURN_RIGHT -> {
                    // 向右偏头 (yaw < -20)
                    coordinator.feedFrame(-25f, 0f, 0.9f, 0.9f)
                }
                LivenessCoordinator.State.PROMPT_FACE_STRAIGHT -> {
                    // 摆正人脸
                    coordinator.feedFrame(2f, 2f, 0.9f, 0.9f)
                }
                else -> {
                    break
                }
            }
        }
    }
}
