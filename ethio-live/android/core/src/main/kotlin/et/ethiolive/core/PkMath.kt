package et.ethiolive.core

/** Display maths for PK battles. Scores and the winner always come from the server. */
object PkMath {
    /** Share of the bar for side A, 0..1. Even split when nobody has scored. */
    fun shareA(scoreA: Long, scoreB: Long): Float {
        val total = scoreA + scoreB
        if (total <= 0) return 0.5f
        return (scoreA.toDouble() / total).toFloat().coerceIn(0.04f, 0.96f)
    }

    /**
     * Seconds left, using the server's clock. [serverOffsetMs] = serverNow - deviceNow measured when the
     * match snapshot arrived, so a wrong phone clock cannot shorten or extend the countdown.
     */
    fun secondsLeft(endsAtEpochMs: Long, deviceNowMs: Long, serverOffsetMs: Long): Long =
        ((endsAtEpochMs - (deviceNowMs + serverOffsetMs)) / 1000).coerceAtLeast(0)

    fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
