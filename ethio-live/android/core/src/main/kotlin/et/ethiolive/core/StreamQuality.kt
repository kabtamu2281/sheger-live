package et.ethiolive.core

/** Encoder profiles. LOW is tuned for congested 3G/4G, which is common outside Addis Ababa. */
enum class StreamQuality(val width: Int, val height: Int, val fps: Int, val bitrateKbps: Int) {
    LOW(360, 640, 15, 400),
    STANDARD(540, 960, 15, 800),
    HIGH(720, 1280, 24, 1500);

    fun lower(): StreamQuality = entries.getOrElse(ordinal - 1) { this }
    fun higher(): StreamQuality = entries.getOrElse(ordinal + 1) { this }
}

/** What the user picked in settings. AUTO lets [QualityAdapter] move between profiles. */
enum class QualityPreference { AUTO, DATA_SAVER, STANDARD, HIGH }

/**
 * Adapts the publish profile to network quality reports (0 = unknown, 1 excellent ... 6 down, as Agora reports).
 * Steps down after 2 consecutive poor reports and up only after 6 consecutive good ones, to avoid flapping.
 */
class QualityAdapter(private val preference: QualityPreference, start: StreamQuality = initial(preference)) {
    var current: StreamQuality = start
        private set
    private var poorStreak = 0
    private var goodStreak = 0

    /** Returns the new profile when it changes, otherwise null. */
    fun onNetworkQuality(score: Int): StreamQuality? {
        if (preference != QualityPreference.AUTO || score == 0) return null
        if (score >= 4) { poorStreak++; goodStreak = 0 } else if (score <= 2) { goodStreak++; poorStreak = 0 } else { poorStreak = 0; goodStreak = 0 }
        val next = when {
            poorStreak >= 2 -> current.lower().also { poorStreak = 0 }
            goodStreak >= 6 -> current.higher().also { goodStreak = 0 }
            else -> current
        }
        return if (next != current) { current = next; next } else null
    }

    companion object {
        fun initial(p: QualityPreference) = when (p) {
            QualityPreference.DATA_SAVER -> StreamQuality.LOW
            QualityPreference.HIGH -> StreamQuality.HIGH
            else -> StreamQuality.STANDARD
        }
    }
}
