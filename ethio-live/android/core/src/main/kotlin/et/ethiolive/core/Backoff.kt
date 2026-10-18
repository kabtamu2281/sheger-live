package et.ethiolive.core

import kotlin.random.Random

/** Exponential backoff with full jitter for reconnects: 1s, 2s, 4s ... capped at [maxMs]. */
class Backoff(private val baseMs: Long = 1_000, private val maxMs: Long = 30_000, private val random: Random = Random.Default) {
    var attempt = 0
        private set

    fun nextDelayMs(): Long {
        val ceiling = (baseMs shl attempt.coerceAtMost(16)).coerceAtMost(maxMs)
        attempt++
        return baseMs / 2 + random.nextLong(ceiling - baseMs / 2 + 1)
    }

    fun reset() { attempt = 0 }
}
