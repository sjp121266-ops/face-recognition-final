package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRecognitionStabilizerTest {

    @Test
    fun labelIsConfirmedAfterTwoStableFrames() {
        val stabilizer = LiveRecognitionStabilizer()
        val first = stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        val second = stabilizer.stabilize(listOf(observation("张三", "张三 85.7%")), nowMs = 1_500L)

        assertEquals(LiveRecognitionStabilizer.LABEL_ANALYZING, first.single().displayLabel)
        assertFalse(first.single().isConfirmed)
        assertEquals(1, first.single().debug.trackId)
        assertTrue(first.single().debug.isNewTrack)
        assertEquals(LiveRecognitionStabilizer.MatchType.NEW, first.single().debug.matchType)
        assertEquals(1, first.single().debug.winningVoteCount)
        assertEquals("张三 85.7%", second.single().displayLabel)
        assertTrue(second.single().isConfirmed)
        assertEquals(2, second.single().debug.winningVoteCount)
        assertEquals(2, second.single().debug.requiredStableFrames)
    }

    @Test
    fun oneFrameLabelChangeKeepsPreviousStableResult() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_500L)

        val changed = stabilizer.stabilize(listOf(observation("李四", "李四 82.0%")), nowMs = 2_000L)

        assertEquals("张三 86.0%", changed.single().displayLabel)
        assertTrue(changed.single().isConfirmed)
        assertEquals(2, changed.single().debug.voteCounts["张三"])
        assertEquals(1, changed.single().debug.voteCounts["李四"])
    }

    @Test
    fun voteWindowResistsSingleNoisyFrame() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        stabilizer.stabilize(listOf(observation("张三", "张三 85.8%")), nowMs = 1_500L)
        stabilizer.stabilize(listOf(observation("李四", "李四 82.0%")), nowMs = 2_000L)

        val recovered = stabilizer.stabilize(listOf(observation("张三", "张三 86.1%")), nowMs = 2_500L)

        assertEquals("张三 86.1%", recovered.single().displayLabel)
        assertTrue(recovered.single().isConfirmed)
    }

    @Test
    fun stableResultChangesAfterNewIdentityWinsVote() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        stabilizer.stabilize(listOf(observation("张三", "张三 85.8%")), nowMs = 1_500L)
        stabilizer.stabilize(listOf(observation("李四", "李四 82.0%")), nowMs = 2_000L)
        stabilizer.stabilize(listOf(observation("李四", "李四 83.0%")), nowMs = 2_500L)

        val changed = stabilizer.stabilize(listOf(observation("李四", "李四 84.0%")), nowMs = 3_000L)

        assertEquals("李四 84.0%", changed.single().displayLabel)
        assertTrue(changed.single().isConfirmed)
    }

    @Test
    fun expiredTrackStartsFresh() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_500L)

        val afterExpiry = stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 6_000L)

        assertEquals(LiveRecognitionStabilizer.LABEL_ANALYZING, afterExpiry.single().displayLabel)
        assertFalse(afterExpiry.single().isConfirmed)
    }

    @Test
    fun twoFacesDoNotReuseSameTrackInOneFrame() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)

        val frame = stabilizer.stabilize(
            listOf(
                observation("张三", "张三 85.0%"),
                observation("李四", "李四 84.0%", left = 32, right = 132)
            ),
            nowMs = 1_500L
        )

        assertTrue(frame.first().isConfirmed)
        assertFalse(frame.last().isConfirmed)
        assertTrue(frame.first().debug.trackId != frame.last().debug.trackId)
    }

    @Test
    fun centerFallbackDoesNotReuseTrackWhenFaceSizeChangesTooMuch() {
        val stabilizer = LiveRecognitionStabilizer()
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_000L)
        stabilizer.stabilize(listOf(observation("张三", "张三 86.0%")), nowMs = 1_500L)

        val changedSize = stabilizer.stabilize(
            listOf(
                observation(
                    identityKey = "李四",
                    displayLabel = "李四 84.0%",
                    left = -40,
                    top = -40,
                    right = 180,
                    bottom = 200
                )
            ),
            nowMs = 2_000L
        )

        assertEquals(LiveRecognitionStabilizer.LABEL_ANALYZING, changedSize.single().displayLabel)
        assertFalse(changedSize.single().isConfirmed)
    }

    private fun observation(
        identityKey: String,
        displayLabel: String,
        left: Int = 10,
        top: Int = 20,
        right: Int = 110,
        bottom: Int = 140
    ): LiveRecognitionStabilizer.Observation {
        return LiveRecognitionStabilizer.Observation(
            bounds = LiveRecognitionStabilizer.Bounds(left, top, right, bottom),
            identityKey = identityKey,
            displayLabel = displayLabel,
            isKnown = true
        )
    }
}
