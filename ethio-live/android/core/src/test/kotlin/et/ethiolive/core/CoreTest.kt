package et.ethiolive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class MoneyTest {
    @Test fun formatsBirr() {
        assertEquals("ETB 1,350.00", Money.format(135000))
        assertEquals("1,350.00 ብር", Money.format(135000, "ETB", AppLanguage.AMHARIC))
        assertEquals("USD 0.99", Money.format(99, "usd"))
    }

    @Test fun compactCounts() {
        assertEquals("950", Money.compact(950))
        assertEquals("1K", Money.compact(1000))
        assertEquals("12.5K", Money.compact(12_540))
        assertEquals("3.2M", Money.compact(3_200_000))
    }

    @Test fun payoutEstimateMatchesServer() {
        // Server: 10000 beans at 50 cents per 100 beans, 2% fee -> 5000 gross, 100 fee, 4900 net.
        assertEquals(PayoutEstimate(400_000, 8_000, 392_000), Money.payoutEstimate(10_000, 4_000, 200))
    }
}

class ServerErrorTest {
    @Test fun parsesCodesFromPostgrestMessages() {
        assertEquals(ServerError.INSUFFICIENT_BALANCE, ServerError.parse("P0001: insufficient_balance"))
        assertEquals(ServerError.RATE_LIMITED, ServerError.parse("{\"code\":\"P0001\",\"message\":\"rate_limited\"}"))
        assertEquals(ServerError.UNKNOWN, ServerError.parse("something exploded"))
        assertEquals(ServerError.UNKNOWN, ServerError.parse(null))
    }
}

class ValidationTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun usernames() {
        assertNull(Validation.usernameProblem("abebe_b"))
        assertEquals(ServerError.USERNAME_INVALID, Validation.usernameProblem("ab"))
        assertEquals(ServerError.USERNAME_INVALID, Validation.usernameProblem("አበበ"))
        assertEquals(ServerError.USERNAME_RESERVED, Validation.usernameProblem("EthioLive_help"))
    }

    @Test fun ageRules() {
        assertEquals(ServerError.AGE_INELIGIBLE, Validation.signupAgeProblem(LocalDate.of(2012, 1, 1), today))
        assertNull(Validation.signupAgeProblem(LocalDate.of(2010, 10, 17), today))
        assertTrue(!Validation.isAdult(LocalDate.of(2008, 10, 18), today))
        assertTrue(Validation.isAdult(LocalDate.of(2008, 10, 17), today))
        assertEquals(ServerError.INVALID_BIRTH_DATE, Validation.signupAgeProblem(LocalDate.of(2030, 1, 1), today))
    }

    @Test fun ethiopianPhoneNumbers() {
        assertEquals("+251911234567", Validation.normalizePhone("0911 234 567"))
        assertEquals("+251911234567", Validation.normalizePhone("911234567"))
        assertEquals("+251911234567", Validation.normalizePhone("+251 91 123 4567"))
        assertEquals("+251911234567", Validation.normalizePhone("00251911234567"))
        assertEquals("+254712345678", Validation.normalizePhone("+254712345678"))
        assertNull(Validation.normalizePhone("12"))
    }

    @Test fun hashtags() {
        assertEquals(listOf("buna", "addis", "ቡና"), Validation.hashtags("#buna #Addis, ቡና #buna ##"))
    }
}

class SeatLayoutTest {
    @Test fun everySupportedLayoutPlacesEachSeatOnce() {
        for (n in SeatLayout.supported) {
            val grid = SeatLayout.grid(n)
            assertEquals(n, grid.cells.size)
            assertEquals((0 until n).toList(), grid.cells.map { it.seatIndex }.sorted())
            val occupied = grid.cells.flatMap { c -> (c.row until c.row + c.rowSpan).flatMap { r -> (c.column until c.column + c.columnSpan).map { r to it } } }
            assertEquals("no overlaps for $n", occupied.size, occupied.toSet().size)
            assertTrue(occupied.all { (r, c) -> r < grid.rows && c < grid.columns })
        }
    }

    @Test fun audioRows() {
        assertEquals(listOf(1, 4, 4), SeatLayout.audioRows(9))
        assertEquals(listOf(1, 4, 1), SeatLayout.audioRows(6))
    }
}

class PkMathTest {
    @Test fun share() {
        assertEquals(0.5f, PkMath.shareA(0, 0))
        assertEquals(0.75f, PkMath.shareA(300, 100))
        assertEquals(0.96f, PkMath.shareA(1000, 0))
    }

    @Test fun countdownUsesServerClock() {
        // Phone clock is 30 s behind the server: the server says 60 s remain, not 90.
        val serverNow = 1_000_000L
        val deviceNow = serverNow - 30_000
        assertEquals(60, PkMath.secondsLeft(serverNow + 60_000, deviceNow, serverNow - deviceNow))
        assertEquals(0, PkMath.secondsLeft(serverNow - 5_000, deviceNow, serverNow - deviceNow))
        assertEquals("4:05", PkMath.clock(245))
    }
}

class QualityAdapterTest {
    @Test fun stepsDownOnPoorNetworkAndUpSlowly() {
        val q = QualityAdapter(QualityPreference.AUTO)
        assertEquals(StreamQuality.STANDARD, q.current)
        assertNull(q.onNetworkQuality(5))
        assertEquals(StreamQuality.LOW, q.onNetworkQuality(5))
        repeat(5) { assertNull(q.onNetworkQuality(1)) }
        assertEquals(StreamQuality.STANDARD, q.onNetworkQuality(1))
    }

    @Test fun fixedPreferenceNeverChanges() {
        val q = QualityAdapter(QualityPreference.DATA_SAVER)
        repeat(10) { assertNull(q.onNetworkQuality(1)) }
        assertEquals(StreamQuality.LOW, q.current)
    }
}

class BackoffTest {
    @Test fun growsAndCaps() {
        val b = Backoff(1_000, 8_000, Random(1))
        val delays = List(8) { b.nextDelayMs() }
        assertTrue(delays.all { it in 500..8_000 })
        b.reset()
        assertEquals(0, b.attempt)
    }
}

class ChatBufferTest {
    @Test fun dedupesOrdersAndCaps() {
        val buf = ChatBuffer<Pair<Long, String>>(3) { it.first }
        buf.addAll(listOf(2L to "b", 1L to "a"))
        val out = buf.addAll(listOf(2L to "b", 4L to "d", 3L to "c"))
        assertEquals(listOf(2L, 3L, 4L), out.map { it.first })
        assertEquals(listOf(2L, 4L), buf.remove(3).map { it.first })
    }

    @Test fun clearThenReloadKeepsOnlyNewItems() {
        val buf = ChatBuffer<Pair<Long, String>>(10) { it.first }
        buf.addAll(listOf(1L to "a", 2L to "b"))
        buf.clear()
        assertEquals(listOf(5L), buf.addAll(listOf(5L to "e")).map { it.first })
    }
}

class GiftComboTest {
    @Test fun combosWithinWindow() {
        val c = GiftCombo(3_000)
        assertEquals(1, c.register("u", "rose", "h", 1, 0))
        assertEquals(6, c.register("u", "rose", "h", 5, 2_000))
        assertEquals(1, c.register("u", "rose", "h", 1, 10_000))
    }
}

class TimeFormatTest {
    @Test fun formats() {
        assertEquals("now", TimeFormat.relative(0, 30_000) { "date" })
        assertEquals("5m", TimeFormat.relative(0, 300_000) { "date" })
        assertEquals("date", TimeFormat.relative(0, 30L * 86_400_000) { "date" })
        assertEquals("1:01:05", TimeFormat.duration(3665))
    }
}
