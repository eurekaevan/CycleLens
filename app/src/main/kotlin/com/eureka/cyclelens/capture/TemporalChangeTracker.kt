package com.eureka.cyclelens.capture

internal data class TemporalTrackingResult(
    val events: List<TemporalChangeEvent>,
    val activeTrackCount: Int,
)

/** A deliberately small overlap tracker; it groups change episodes, not game objects. */
internal class TemporalChangeTracker(
    private val config: TemporalDetectionConfig,
) {
    private data class Track(
        val id: Long,
        var candidate: EventCandidate,
        var ageFrames: Int,
        var active: Boolean,
    )

    private val tracks = mutableListOf<Track>()
    private val matchedTrackIds = LongArray(config.maxTrackedChanges)
    private var nextTrackId = 1L

    fun update(candidates: List<EventCandidate>, timestampNs: Long): TemporalTrackingResult {
        val events = ArrayList<TemporalChangeEvent>(candidates.size + tracks.size)
        var matchedTrackCount = 0

        // A gap beyond cooldown cannot belong to the same observed episode. Close it before new
        // START events so event order remains deterministic even when frames were dropped.
        tracks.forEach { track ->
            if (track.active &&
                elapsed(timestampNs, track.candidate.timestampNs) >= config.trackCooldownNs
            ) {
                track.active = false
                events += track.event(TemporalChangePhase.END)
            }
        }
        tracks.removeAll { track ->
            !track.active && elapsed(timestampNs, track.candidate.timestampNs) >= config.trackCooldownNs
        }

        for (candidate in candidates) {
            var match: Track? = null
            var bestIou = config.trackingIouThreshold
            for (track in tracks) {
                if (isMatched(track.id, matchedTrackCount) ||
                    elapsed(timestampNs, track.candidate.timestampNs) >= config.trackCooldownNs
                ) continue
                val iou = normalizedIou(candidate.bounds, track.candidate.bounds)
                if (iou > bestIou || iou == bestIou && (match == null || track.id < match.id)) {
                    match = track
                    bestIou = iou
                }
            }
            if (match == null) {
                if (tracks.size >= config.maxTrackedChanges) {
                    val victim = tracks.minWithOrNull(
                        compareBy<Track> { it.active }.thenBy { it.candidate.timestampNs }.thenBy { it.id },
                    )
                    if (victim != null) {
                        if (victim.active) events += victim.event(TemporalChangePhase.END)
                        tracks.remove(victim)
                    }
                }
                val track = Track(nextTrackId++, candidate, ageFrames = 1, active = true)
                tracks += track
                matchedTrackIds[matchedTrackCount++] = track.id
                events += track.event(TemporalChangePhase.START)
            } else {
                match.candidate = candidate
                match.ageFrames++
                match.active = true
                matchedTrackIds[matchedTrackCount++] = match.id
                events += match.event(TemporalChangePhase.UPDATE)
            }
        }

        tracks.forEach { track ->
            if (track.active && !isMatched(track.id, matchedTrackCount) &&
                elapsed(timestampNs, track.candidate.timestampNs) >= config.trackExpirationNs
            ) {
                track.active = false
                events += track.event(TemporalChangePhase.END)
            }
        }
        tracks.removeAll { track ->
            !track.active && elapsed(timestampNs, track.candidate.timestampNs) >= config.trackCooldownNs
        }
        return TemporalTrackingResult(events, tracks.count { it.active })
    }

    fun reset() {
        tracks.clear()
        nextTrackId = 1L
    }

    private fun Track.event(phase: TemporalChangePhase) = TemporalChangeEvent(
        trackId = id,
        phase = phase,
        candidate = candidate,
        ageFrames = ageFrames,
    )

    private fun elapsed(nowNs: Long, thenNs: Long): Long = (nowNs - thenNs).coerceAtLeast(0L)

    private fun isMatched(trackId: Long, count: Int): Boolean {
        for (index in 0 until count) {
            if (matchedTrackIds[index] == trackId) return true
        }
        return false
    }

    companion object {
        internal fun normalizedIou(first: NormalizedRect, second: NormalizedRect): Float {
            val intersectionWidth = (minOf(first.right, second.right) -
                maxOf(first.left, second.left)).coerceAtLeast(0f)
            val intersectionHeight = (minOf(first.bottom, second.bottom) -
                maxOf(first.top, second.top)).coerceAtLeast(0f)
            val intersection = intersectionWidth * intersectionHeight
            if (intersection <= 0f) return 0f
            val firstArea = (first.right - first.left) * (first.bottom - first.top)
            val secondArea = (second.right - second.left) * (second.bottom - second.top)
            return intersection / (firstArea + secondArea - intersection)
        }
    }
}
