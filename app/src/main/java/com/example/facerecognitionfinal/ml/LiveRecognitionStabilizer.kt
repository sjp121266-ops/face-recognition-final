package com.example.facerecognitionfinal.ml

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Collections
import java.util.IdentityHashMap

class LiveRecognitionStabilizer(
    private val requiredStableFrames: Int = DEFAULT_REQUIRED_STABLE_FRAMES,
    private val maxTrackAgeMs: Long = DEFAULT_MAX_TRACK_AGE_MS,
    private val voteWindowSize: Int = DEFAULT_VOTE_WINDOW_SIZE
) {
    private val tracks = mutableListOf<Track>()
    private var nextTrackId = 1

    fun stabilize(observations: List<Observation>, nowMs: Long = System.currentTimeMillis()): List<StableObservation> {
        tracks.removeAll { nowMs - it.updatedAtMs > maxTrackAgeMs }
        // Limit max tracks
        if (tracks.size > 20) {
            tracks.sortByDescending { it.updatedAtMs }
            while (tracks.size > 20) tracks.removeAt(tracks.lastIndex)
        }
        val usedTracks = Collections.newSetFromMap(IdentityHashMap<Track, Boolean>())

        return observations.map { observation ->
            val match = findTrack(observation, usedTracks)
            val updated = if (match == null) {
                Track(
                    trackId = nextTrackId++,
                    bounds = observation.bounds,
                    displayLabel = observation.displayLabel,
                    stableDisplayLabel = null,
                    votes = mutableListOf(observation.toVote()),
                    isKnown = observation.isKnown,
                    updatedAtMs = nowMs
                ).also { tracks.add(it) }
            } else {
                updateTrack(match.track, observation, nowMs)
            }
            usedTracks.add(updated)
            StableObservation(
                bounds = observation.bounds,
                displayLabel = updated.stableDisplayLabel ?: LABEL_ANALYZING,
                isKnown = updated.isKnown && updated.stableDisplayLabel != null,
                isConfirmed = updated.stableDisplayLabel != null,
                debug = debugFor(updated, observation, match)
            )
        }
    }

    fun reset() {
        tracks.clear()
        nextTrackId = 1
    }

    private fun findTrack(observation: Observation, usedTracks: Set<Track>): TrackMatch? {
        val iouMatch = tracks
            .filterNot { it in usedTracks }
            .map { track -> track to track.bounds.iou(observation.bounds) }
            .filter { (_, iou) -> iou >= MIN_IOU }
            .maxByOrNull { (_, iou) -> iou }
        if (iouMatch != null) {
            return TrackMatch(
                track = iouMatch.first,
                matchType = MatchType.IOU,
                iou = iouMatch.second,
                centerDistancePx = iouMatch.first.bounds.centerDistanceTo(observation.bounds)
            )
        }
        return tracks
            .filterNot { it in usedTracks }
            .filter { it.bounds.canFallbackMatch(observation.bounds) }
            .minByOrNull { it.bounds.centerDistanceTo(observation.bounds) }
            ?.let { track ->
                TrackMatch(
                    track = track,
                    matchType = MatchType.CENTER_FALLBACK,
                    iou = track.bounds.iou(observation.bounds),
                    centerDistancePx = track.bounds.centerDistanceTo(observation.bounds)
                )
            }
    }

    private fun updateTrack(track: Track, observation: Observation, nowMs: Long): Track {
        track.votes.add(observation.toVote())
        while (track.votes.size > voteWindowSize) {
            track.votes.removeAt(0)
        }

        val winner = track.winningVote()
        if (winner != null) {
            track.stableDisplayLabel = winner.displayLabel
            track.isKnown = winner.isKnown
        }
        track.bounds = observation.bounds
        track.displayLabel = observation.displayLabel
        track.updatedAtMs = nowMs
        return track
    }

    private fun Observation.toVote(): Vote {
        return Vote(identityKey = identityKey, displayLabel = displayLabel, isKnown = isKnown)
    }

    private fun Track.winningVote(): Vote? {
        return votes
            .groupBy { it.identityKey to it.isKnown }
            .values
            .filter { group -> group.size >= requiredStableFrames }
            .maxWithOrNull(
                compareBy<List<Vote>> { it.size }
                    .thenBy { group -> votes.indexOf(group.last()) }
            )
            ?.last()
    }

    private fun debugFor(
        track: Track,
        observation: Observation,
        match: TrackMatch?
    ): StabilizationDebug {
        val groupedVotes = track.votes.groupingBy { it.identityKey }.eachCount()
        val winningVoteCount = groupedVotes.maxOfOrNull { it.value } ?: 0
        return StabilizationDebug(
            trackId = track.trackId,
            isNewTrack = match == null,
            matchType = match?.matchType ?: MatchType.NEW,
            iou = match?.iou ?: 0f,
            centerDistancePx = match?.centerDistancePx ?: 0,
            voteCounts = groupedVotes,
            winningVoteCount = winningVoteCount,
            requiredStableFrames = requiredStableFrames,
            voteWindowSize = voteWindowSize,
            observedKey = observation.identityKey,
            stableLabel = track.stableDisplayLabel
        )
    }

    data class Observation(
        val bounds: Bounds,
        val identityKey: String,
        val displayLabel: String,
        val isKnown: Boolean
    )

    data class StableObservation(
        val bounds: Bounds,
        val displayLabel: String,
        val isKnown: Boolean,
        val isConfirmed: Boolean,
        val debug: StabilizationDebug
    )

    data class StabilizationDebug(
        val trackId: Int,
        val isNewTrack: Boolean,
        val matchType: MatchType,
        val iou: Float,
        val centerDistancePx: Int,
        val voteCounts: Map<String, Int>,
        val winningVoteCount: Int,
        val requiredStableFrames: Int,
        val voteWindowSize: Int,
        val observedKey: String,
        val stableLabel: String?
    ) {
        fun formatCompact(): String {
            val stable = stableLabel ?: LABEL_ANALYZING
            return "T$trackId ${matchType.label} vote $winningVoteCount/$requiredStableFrames stable=$stable"
        }
    }

    enum class MatchType(val label: String) {
        IOU("IOU"),
        CENTER_FALLBACK("CENTER"),
        NEW("NEW")
    }

    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        private val width: Int get() = max(0, right - left)
        private val height: Int get() = max(0, bottom - top)
        private val area: Int get() = width * height
        private val centerX: Int get() = left + width / 2
        private val centerY: Int get() = top + height / 2

        fun iou(other: Bounds): Float {
            val overlapLeft = max(left, other.left)
            val overlapTop = max(top, other.top)
            val overlapRight = min(right, other.right)
            val overlapBottom = min(bottom, other.bottom)
            val overlapWidth = max(0, overlapRight - overlapLeft)
            val overlapHeight = max(0, overlapBottom - overlapTop)
            val intersection = overlapWidth * overlapHeight
            val union = area + other.area - intersection
            return if (union <= 0) 0f else intersection / union.toFloat()
        }

        fun centerDistanceTo(other: Bounds): Int {
            return abs(centerX - other.centerX) + abs(centerY - other.centerY)
        }

        fun canFallbackMatch(other: Bounds): Boolean {
            if (centerDistanceTo(other) > dynamicCenterDistanceLimit(other)) return false
            return sizeSimilarityTo(other) >= MIN_SIZE_SIMILARITY
        }

        private fun dynamicCenterDistanceLimit(other: Bounds): Int {
            val faceScale = max(max(width, height), max(other.width, other.height))
            return min(MAX_CENTER_DISTANCE_PX, (faceScale * CENTER_DISTANCE_RATIO).toInt())
        }

        private fun sizeSimilarityTo(other: Bounds): Float {
            val widthSimilarity = minDimensionRatio(width, other.width)
            val heightSimilarity = minDimensionRatio(height, other.height)
            return min(widthSimilarity, heightSimilarity)
        }

        private fun minDimensionRatio(first: Int, second: Int): Float {
            val larger = max(first, second)
            if (larger <= 0) return 0f
            return min(first, second) / larger.toFloat()
        }
    }

    private data class Track(
        val trackId: Int,
        var bounds: Bounds,
        var displayLabel: String,
        var stableDisplayLabel: String?,
        val votes: MutableList<Vote>,
        var isKnown: Boolean,
        var updatedAtMs: Long
    )

    private data class TrackMatch(
        val track: Track,
        val matchType: MatchType,
        val iou: Float,
        val centerDistancePx: Int
    )

    private data class Vote(
        val identityKey: String,
        val displayLabel: String,
        val isKnown: Boolean
    )

    companion object {
        const val LABEL_ANALYZING = "识别中..."
        private const val DEFAULT_REQUIRED_STABLE_FRAMES = 2
        private const val DEFAULT_VOTE_WINDOW_SIZE = 5
        private const val DEFAULT_MAX_TRACK_AGE_MS = 8_000L  // Increased from 4s for better persistence
        private const val MIN_IOU = 0.25f
        private const val MAX_CENTER_DISTANCE_PX = 120
        private const val CENTER_DISTANCE_RATIO = 0.55f
        private const val MIN_SIZE_SIMILARITY = 0.65f
    }
}
