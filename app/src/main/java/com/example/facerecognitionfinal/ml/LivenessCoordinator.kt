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
        FACE_STRAIGHT,
        NOD
    }

    enum class State {
        INACTIVE,
        PROMPT_BLINK,
        PROMPT_TURN_LEFT,
        PROMPT_TURN_RIGHT,
        PROMPT_FACE_STRAIGHT,
        PROMPT_NOD,
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
    private var firstStableStraightFrameAt = 0L
    private var nodStage = 0

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
        
        // Keep the live check short for classroom/mobile lighting: one active
        // challenge plus a straight-face confirmation is less likely to miss
        // ML Kit eye probabilities on low-end cameras.
        if (Random.nextBoolean()) {
            actionSequence.add(Action.BLINK)
        } else {
            actionSequence.add(turnAction)
        }
        actionSequence.add(Action.FACE_STRAIGHT)

        currentActionIndex = 0
        blinkStage = 0
        firstStableStraightFrameAt = 0L
        nodStage = 0
        
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
        firstStableStraightFrameAt = 0L
        nodStage = 0
    }

    fun feedFrame(
        yawDegrees: Float,
        rollDegrees: Float,
        leftEyeOpenProb: Float?,
        rightEyeOpenProb: Float?,
        pitchDegrees: Float = 0f
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
                val leftEye = leftEyeOpenProb
                val rightEye = rightEyeOpenProb
                if (leftEye != null && rightEye != null) {
                    val averageEyeOpen = (leftEye + rightEye) / 2f
                    val eitherEyeClosed = leftEye < EYE_CLOSED_THRESHOLD || rightEye < EYE_CLOSED_THRESHOLD
                    val eyesReopened = leftEye > EYE_REOPEN_THRESHOLD && rightEye > EYE_REOPEN_THRESHOLD

                    when (blinkStage) {
                        0 -> {
                            // 初始：先需要是睁眼状态
                            if (averageEyeOpen > EYE_OPEN_THRESHOLD) {
                                blinkStage = 1
                            }
                        }
                        1 -> {
                            // 步骤1：检测到眼睛闭上
                            if (eitherEyeClosed || averageEyeOpen < EYE_AVERAGE_CLOSED_THRESHOLD) {
                                blinkStage = 2
                            }
                        }
                        2 -> {
                            // 步骤2：眼睛重新睁开，完成眨眼
                            if (eyesReopened || averageEyeOpen > EYE_OPEN_THRESHOLD) {
                                moveToNextAction()
                            }
                        }
                    }
                } else if (detectNodFallback(pitchDegrees)) {
                    moveToNextAction()
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
                    if (firstStableStraightFrameAt == 0L) {
                        firstStableStraightFrameAt = now
                    }
                    if (now - firstStableStraightFrameAt >= STRAIGHT_HOLD_MS) {
                        currentState = State.VERIFIED
                    }
                } else {
                    firstStableStraightFrameAt = 0L
                }
            }
            Action.NOD -> {
                if (detectNodFallback(pitchDegrees)) {
                    moveToNextAction()
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
        firstStableStraightFrameAt = 0L
        nodStage = 0
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
            Action.NOD -> State.PROMPT_NOD
        }
    }

    private fun detectNodFallback(pitchDegrees: Float): Boolean {
        when (nodStage) {
            0 -> {
                if (abs(pitchDegrees) < PITCH_STRAIGHT_THRESHOLD) {
                    nodStage = 1
                }
            }
            1 -> {
                if (abs(pitchDegrees) > PITCH_NOD_THRESHOLD) {
                    nodStage = 2
                }
            }
            2 -> {
                if (abs(pitchDegrees) < PITCH_STRAIGHT_THRESHOLD) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        const val DEFAULT_STEP_TIMEOUT_MS = 9000L // 每步限时 9 秒，给低帧率设备更多余量
        
        private const val EYE_CLOSED_THRESHOLD = 0.28f
        private const val EYE_AVERAGE_CLOSED_THRESHOLD = 0.36f
        private const val EYE_OPEN_THRESHOLD = 0.50f
        private const val EYE_REOPEN_THRESHOLD = 0.42f
        private const val YAW_TURN_THRESHOLD = 14.0f
        private const val YAW_STRAIGHT_THRESHOLD = 12.0f
        private const val ROLL_STRAIGHT_THRESHOLD = 12.0f
        private const val PITCH_NOD_THRESHOLD = 10.0f
        private const val PITCH_STRAIGHT_THRESHOLD = 6.0f
        private const val STRAIGHT_HOLD_MS = 250L
    }
}
