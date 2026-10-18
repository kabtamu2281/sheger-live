package et.ethiolive.core

/** Compact relative times for chat lists: now, 5m, 3h, 2d, then a date. */
object TimeFormat {
    fun relative(thenMs: Long, nowMs: Long, dateFallback: (Long) -> String): String {
        val s = (nowMs - thenMs) / 1000
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            s < 7 * 86_400 -> "${s / 86_400}d"
            else -> dateFallback(thenMs)
        }
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val sec = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
