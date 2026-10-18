package et.ethiolive.core

/** A seat's position in a grid, in cell units. */
data class SeatCell(val seatIndex: Int, val row: Int, val column: Int, val rowSpan: Int = 1, val columnSpan: Int = 1)

data class SeatGrid(val rows: Int, val columns: Int, val cells: List<SeatCell>)

/**
 * Video layouts for multi-guest rooms. Supported sizes are 1, 2, 4, 6 and 9 (the server enforces the same set).
 * Seat 0 is always the host. In 6-seat rooms the host gets a 2x2 tile.
 */
object SeatLayout {
    val supported = listOf(1, 2, 4, 6, 9)

    fun grid(maxSeats: Int): SeatGrid = when (maxSeats) {
        1 -> SeatGrid(1, 1, listOf(SeatCell(0, 0, 0)))
        2 -> SeatGrid(1, 2, listOf(SeatCell(0, 0, 0), SeatCell(1, 0, 1)))
        4 -> SeatGrid(2, 2, (0 until 4).map { SeatCell(it, it / 2, it % 2) })
        6 -> SeatGrid(3, 3, listOf(SeatCell(0, 0, 0, 2, 2), SeatCell(1, 0, 2), SeatCell(2, 1, 2),
            SeatCell(3, 2, 0), SeatCell(4, 2, 1), SeatCell(5, 2, 2)))
        9 -> SeatGrid(3, 3, (0 until 9).map { SeatCell(it, it / 3, it % 3) })
        else -> throw IllegalArgumentException("unsupported seat count $maxSeats")
    }

    /** Seat counts a host may pick for a mode (gaming streams are single-seat). */
    fun choicesFor(mode: RoomMode): List<Int> = when (mode) {
        RoomMode.GAMING -> listOf(1)
        RoomMode.VIDEO -> listOf(1, 2, 4)
        RoomMode.MULTI_GUEST -> listOf(2, 4, 6, 9)
        RoomMode.AUDIO -> listOf(4, 6, 9)
    }

    /** Audio rooms: host on top, speakers in rows of up to four. Returns row sizes. */
    fun audioRows(maxSeats: Int): List<Int> {
        val rest = maxSeats - 1
        return listOf(1) + List((rest + 3) / 4) { i -> minOf(4, rest - i * 4) }
    }
}

enum class RoomMode(val wire: String) {
    VIDEO("video"), AUDIO("audio"), MULTI_GUEST("multi_guest"), GAMING("gaming");

    companion object { fun fromWire(v: String) = entries.first { it.wire == v } }
}
