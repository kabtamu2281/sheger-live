package et.bizassistant.core

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class AssistantEngineTest {
    private val profile = BusinessProfile("Sheger Cafe", phone = "0911000000")
    private val messageInputs = mapOf(
        Fields.CUSTOMER_NAME to "Almaz",
        Fields.PURPOSE to "payment_reminder",
        Fields.DETAILS to "Invoice INV-7 for ETB 1,250.50 is due on 2026-10-30.",
    )
    private val allowed = ConsentState(true, 1L)
    private val quota = UsageQuota(PlanId.FREE, used = 3, limit = 20, resetsOn = LocalDate.of(2026, 11, 1))
    private val neverCalled = AiClient { fail("AI must not be contacted") }

    private fun engine(ai: AiClient, consent: ConsentState = allowed, q: UsageQuota? = quota) =
        AssistantEngine(ai, { consent }, { q })

    @Test
    fun asksForMissingInformationBeforeProducingAnything() {
        val outcome = engine(neverCalled).run(
            TaskType.CUSTOMER_MESSAGE, mapOf(Fields.CUSTOMER_NAME to "Almaz", Fields.DETAILS to " "), profile, AppLanguage.ENGLISH,
        )
        assertIs<TaskOutcome.NeedsInput>(outcome)
        assertEquals(listOf(Fields.PURPOSE, Fields.DETAILS), outcome.missing.map { it.key })
    }

    @Test
    fun neverSendsContentWithoutConsent() {
        for (consent in listOf(ConsentState.UNDECIDED, ConsentState(false, 5L))) {
            val outcome = engine(neverCalled, consent).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.ENGLISH)
            assertIs<TaskOutcome.Draft>(outcome)
            assertEquals(DraftSource.TEMPLATE, outcome.source)
            assertEquals(Str.FALLBACK_NO_CONSENT, outcome.notice)
            assertTrue(outcome.text.contains("Dear Almaz"))
        }
    }

    @Test
    fun aiFailureFallsBackToHelpfulTemplate() {
        val down = AiClient { AiResult.Failure(AiResult.Failure.Kind.UNAVAILABLE, "timeout") }
        val throwing = AiClient { throw java.io.IOException("no network") }
        for (ai in listOf(down, throwing)) {
            val outcome = engine(ai).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.AMHARIC)
            assertIs<TaskOutcome.Draft>(outcome)
            assertEquals(DraftSource.TEMPLATE, outcome.source)
            assertEquals(Str.FALLBACK_AI_UNAVAILABLE, outcome.notice)
            assertTrue(outcome.text.contains("ውድ Almaz"))
            assertTrue(outcome.text.contains("1,250.50"))
        }
    }

    @Test
    fun exhaustedQuotaSkipsAiAndExplains() {
        val used = quota.copy(used = 20)
        val outcome = engine(neverCalled, q = used).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.OROMO)
        assertIs<TaskOutcome.Draft>(outcome)
        assertEquals(Str.FALLBACK_QUOTA, outcome.notice)
        assertTrue(outcome.text.startsWith("Kabajamoo Almaz"))

        val serverSaysNo = AiClient { AiResult.Failure(AiResult.Failure.Kind.QUOTA_EXCEEDED, quota = used) }
        val o2 = engine(serverSaysNo).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.ENGLISH)
        assertIs<TaskOutcome.Draft>(o2)
        assertEquals(Str.FALLBACK_QUOTA, o2.notice)
    }

    @Test
    fun acceptsAiDraftThatOnlyUsesProvidedNumbers() {
        var sent: AiRequest? = null
        val ai = AiClient {
            sent = it
            AiResult.Success("Hello Almaz, a reminder that invoice INV-7 for ETB 1,250.50 is due on 2026-10-30. Call 0911000000.")
        }
        val outcome = engine(ai).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.ENGLISH)
        assertIs<TaskOutcome.Draft>(outcome)
        assertEquals(DraftSource.AI, outcome.source)
        // Only this task's fields, the business name and the language are sent.
        assertEquals(setOf(Fields.CUSTOMER_NAME, Fields.PURPOSE, Fields.DETAILS), sent!!.inputs.keys)
        assertEquals("Sheger Cafe", sent!!.businessName)
        assertTrue(sent!!.facts.isEmpty())
    }

    @Test
    fun rejectsAiDraftWithInventedFigures() {
        val ai = AiClient { AiResult.Success("Dear Almaz, please pay ETB 1,350.00 (incl. 15% VAT) by 2026-10-30.") }
        val outcome = engine(ai).run(TaskType.CUSTOMER_MESSAGE, messageInputs, profile, AppLanguage.ENGLISH)
        assertIs<TaskOutcome.Draft>(outcome)
        assertEquals(DraftSource.TEMPLATE, outcome.source)
        assertEquals(Str.FALLBACK_UNVERIFIED_NUMBERS, outcome.notice)
        assertEquals(listOf("1350, 15"), outcome.noticeArgs)
    }

    @Test
    fun reportSendsOnlyAggregatedFigures() {
        val records = listOf(
            BusinessRecord(type = RecordType.SALE, date = LocalDate.of(2026, 10, 17), description = "Coffee", quantity = BigDecimal(3), unitAmount = Money.parse("35")!!, customerOrSupplier = "Private Customer"),
            BusinessRecord(type = RecordType.EXPENSE, date = LocalDate.of(2026, 10, 17), description = "Milk", unitAmount = Money.parse("40")!!, note = "secret note"),
        )
        val summary = SummaryCalculator.daily(records, LocalDate.of(2026, 10, 17))
        var sent: AiRequest? = null
        val ai = AiClient {
            sent = it
            AiResult.Success("On 2026-10-17 Sheger Cafe made ETB 105.00 in sales from 1 sale and spent ETB 40.00, leaving ETB 65.00.")
        }
        val outcome = engine(ai).run(TaskType.REPORT, mapOf(Fields.REPORT_PERIOD to "today"), profile, AppLanguage.ENGLISH, summary)
        assertIs<TaskOutcome.Draft>(outcome)
        assertEquals(DraftSource.AI, outcome.source, (outcome.notice?.en ?: "") + outcome.noticeArgs)
        val payload = sent!!.facts.values.joinToString() + sent!!.inputs.values.joinToString()
        assertTrue("Private Customer" !in payload && "secret note" !in payload)
        assertEquals("ETB 105.00", sent!!.facts["total_sales"])
    }

    @Test
    fun deterministicTasksAreNotRoutedThroughAi() {
        try {
            engine(neverCalled).run(TaskType.INVOICE, emptyMap(), profile, AppLanguage.ENGLISH)
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun translationFallbackKeepsOriginalText() {
        val outcome = engine(neverCalled, ConsentState.UNDECIDED).run(
            TaskType.TRANSLATION,
            mapOf(Fields.SOURCE_TEXT to "Your order is ready.", Fields.TARGET_LANGUAGE to "am"),
            profile, AppLanguage.ENGLISH,
        )
        assertIs<TaskOutcome.Draft>(outcome)
        assertTrue(outcome.text.endsWith("Your order is ready."))
    }

    @Test
    fun numberGuardNormalizesFormats() {
        assertEquals(setOf("1250.5", "3"), NumberGuard.extract("ETB 1,250.50 for 3 items"))
        assertTrue(NumberGuard.unsupportedNumbers("Total 1250.5", listOf("1,250.50")).isEmpty())
        assertEquals(setOf("99"), NumberGuard.unsupportedNumbers("Only 99 left", listOf("Coffee")))
    }

    @Test
    fun librarySearchMatchesAllTermsInAnyScript() {
        val docs = listOf(
            SearchableDocument(1, "Invoice Almaz", "Coffee 3", "invoice", 10),
            SearchableDocument(2, "ደብዳቤ", "ለአቶ በቀለ የዋጋ ለውጥ", "letter", 20),
            SearchableDocument(3, "Invoice Kebede", "Tea", "invoice", 30),
        )
        assertEquals(listOf(3L, 1L), LibrarySearch.search(docs, "invoice").map { it.id })
        assertEquals(listOf(1L), LibrarySearch.search(docs, "INVOICE coffee").map { it.id })
        assertEquals(listOf(2L), LibrarySearch.search(docs, "በቀለ").map { it.id })
        assertEquals(3, LibrarySearch.search(docs, "  ").size)
    }
}
