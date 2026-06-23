package com.example.facerecognitionfinal.ml

import kotlin.math.abs
import kotlin.random.Random

class LivenessCoordinator(
    private val stepTimeoutMs: Long = DEFAULT_STEP_TIMEOUT_MS
) {

    enum class Action {
        BLINK,
        TURN_LEFT,
        TURN_RIGHT,
        FACE_STRAIGHT
    }

    enum class State {
        INACTIVE,
        PROMPT_BLINK,
        PROMPT_TURN_LEFT,
        PROMPT_TURN_RIGHT,
        PROMPT_FACE_STRAIGHT,
        VERIFIED,
        TIMEOUT
    }

    // 动作序列
    private val actionSequence = mutableListOf<Action>()
    private var currentActionIndex = 0

    var currentState: State = State.INACTIVE
        private set

    private var stateStartTime = 0L
    private var lastStateTransitionTime = 0L

    // 眨眼检测中间状态：0 - 睁眼，1 - 闭眼，2 - 眨眼完成
    private var blinkStage = 0

    val currentStepNumber: Int
        get() = if (currentState == State.INACTIVE || currentState == State.TIMEOUT || currentState == State.VERIFIED) {
            0
        } else {
            currentActionIndex + 1
        }

    val totalSteps: Int
        get() = actionSequence.size

    val isFinished: Boolean
        get() = currentState == State.VERIFIED || currentState == State.TIMEOUT

    fun start() {
        actionSequence.clear()
        
        // 随机选择转脸方向 (向左或向右)
        val turnAction = if (Random.nextBoolean()) Action.TURN_LEFT else Action.TURN_RIGHT
        
        // 随机生成动作序列：眨眼和转脸，最后摆正人脸
        if (Random.nextBoolean()) {
            actionSequence.add(Action.BLINK)
            actionSequence.add(turnAction)
        } else {
            actionSequence.add(turnAction)
            actionSequence.add(Action.BLINK)
        }
        actionSequence.add(Action.FACE_STRAIGHT)

        currentActionIndex = 0
        blinkStage = 0
        
        val now = System.currentTimeMillis()
        stateStartTime = now
        lastStateTransitionTime = now
        
        currentState = mapActionToState(actionSequence[0])
    }

    fun stop() {
        currentState = State.INACTIVE
        actionSequence.clear()
        currentActionIndex = 0
        blinkStage = 0
    }

    fun feedFrame(
        yawDegrees: Float,
        rollDegrees: Float,
        leftEyeOpenProb: Float?,
        rightEyeOpenProb: Float?
    ): State {
        if (currentState == State.INACTIVE || isFinished) {
            return currentState
        }

        val now = System.currentTimeMillis()
        // 检查当前步骤是否超时
        if (now - lastStateTransitionTime > stepTimeoutMs) {
            currentState = State.TIMEOUT
            return currentState
        }

        val currentAction = actionSequence.getOrNull(currentActionIndex) ?: return currentState

        when (currentAction) {
            Action.BLINK -> {
                if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
                    val bothEyesClosed = leftEyeOpenProb < EYE_CLOSED_THRESHOLD && rightEyeOpenProb < EYE_CLOSED_THRESHOLD
                    val bothEyesOpen = leftEyeOpenProb > EYE_OPEN_THRESHOLD && rightEyeOpenProb > EYE_OPEN_THRESHOLD

                    when (blinkStage) {
                        0 -> {
                            // 初始：先需要是睁眼状态
                            if (bothEyesOpen) {
                                blinkStage = 1
                            }
                        }
                        1 -> {
                            // 步骤1：检测到眼睛闭上
                            if (bothEyesClosed) {
                                blinkStage = 2
                            }
                        }
                        2 -> {
                            // 步骤2：眼睛重新睁开，完成眨眼
                            if (bothEyesOpen) {
                                moveToNextAction()
                            }
                        }
                    }
                }
            }
            Action.TURN_LEFT -> {
                if (yawDegrees > YAW_TURN_THRESHOLD) {
                    moveToNextAction()
                }
            }
            Action.TURN_RIGHT -> {
                if (yawDegrees < -YAW_TURN_THRESHOLD) {
                    moveToNextAction()
                }
            }
            Action.FACE_STRAIGHT -> {
                if (abs(yawDegrees) < YAW_STRAIGHT_THRESHOLD && abs(rollDegrees) < ROLL_STRAIGHT_THRESHOLD) {
                    currentState = State.VERIFIED
                }
            }
        }

        return currentState
    }

    fun getRemainingTimeMs(): Long {
        if (currentState == State.INACTIVE || isFinished) return 0L
        val elapsed = System.currentTimeMillis() - lastStateTransitionTime
        return (stepTimeoutMs - elapsed).coerceAtLeast(0L)
    }

    private fun moveToNextAction() {
        currentActionIndex++
        blinkStage = 0
        lastStateTransitionTime = System.currentTimeMillis()

        val nextAction = actionSequence.getOrNull(currentActionIndex)
        if (nextAction != null) {
            currentState = mapActionToState(nextAction)
        } else {
            currentState = State.VERIFIED
        }
    }

    private fun mapActionToState(action: Action): State {
        return when (action) {
            Action.BLINK -> State.PROMPT_BLINK
            Action.TURN_LEFT -> State.PROMPT_TURN_LEFT
            Action.TURN_RIGHT -> State.PROMPT_TURN_RIGHT
            Action.FACE_STRAIGHT -> State.PROMPT_FACE_STRAIGHT
        }
    }

    companion object {
        const val DEFAULT_STEP_TIMEOUT_MS = 6000L // 每步限时 6 秒
        
        private const val EYE_CLOSED_THRESHOLD = 0.15f
        private const val EYE_OPEN_THRESHOLD = 0.65f
        private const val YAW_TURN_THRESHOLD = 20.0f
        private const val YAW_STRAIGHT_THRESHOLD = 8.0f
        private const val ROLL_STRAIGHT_THRESHOLD = 8.0f
    }
}
