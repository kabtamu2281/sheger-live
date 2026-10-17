package et.bizassistant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LanguageTest {

    private val ethiopic = Regex("[\\u1200-\\u139F]")

    @Test
    fun everyStringExistsInEveryLanguage() {
        for (s in Str.entries) for (lang in AppLanguage.entries) {
            assertTrue(s.get(lang).isNotBlank(), "$s missing in $lang")
        }
    }

    @Test
    fun formatPlaceholdersMatchAcrossLanguages() {
        val placeholder = Regex("""%\d\$[sd]""")
        for (s in Str.entries) {
            val en = placeholder.findAll(s.en).map { it.value }.toSet()
            assertEquals(en, placeholder.findAll(s.am).map { it.value }.toSet(), "$s am placeholders")
            assertEquals(en, placeholder.findAll(s.om).map { it.value }.toSet(), "$s om placeholders")
        }
    }

    @Test
    fun amharicUsesEthiopicScriptAndOromoUsesLatin() {
        val exempt = setOf(Str.TIN_OPTIONAL) // proper noun / acronyms
        for (s in Str.entries) {
            if (s in exempt) continue
            assertTrue(ethiopic.containsMatchIn(s.am) || s.am == s.en, "$s Amharic should be in Ethiopic script")
            assertTrue(!ethiopic.containsMatchIn(s.om), "$s Oromo should use Qubee (Latin) script")
        }
    }

    @Test
    fun selectingLanguageChangesRenderedDocument() {
        val doc = SalesDocument(
            kind = DocKind.INVOICE, number = "INV-1", date = java.time.LocalDate.of(2026, 10, 17), customerName = "Almaz",
            items = listOf(LineItem("Teff", java.math.BigDecimal("2"), Money.parse("100")!!)),
        )
        val profile = BusinessProfile("Sheger Shop")
        val en = DocumentRenderer.render(doc, profile, AppLanguage.ENGLISH)
        val am = DocumentRenderer.render(doc, profile, AppLanguage.AMHARIC)
        val om = DocumentRenderer.render(doc, profile, AppLanguage.OROMO)
        assertTrue(en.contains("INVOICE") && en.contains("Total"))
        assertTrue(am.contains("ኢንቮይስ") && am.contains("ጠቅላላ ድምር"))
        assertTrue(om.contains("INIVOOYSII") && om.contains("Waliigala"))
        // The arithmetic is identical in every language.
        listOf(en, am, om).forEach { assertTrue(it.contains("ETB 200.00")) }
        assertNotEquals(en, am)
    }

    @Test
    fun languageTagsRoundTrip() {
        assertEquals(AppLanguage.AMHARIC, AppLanguage.fromTag("am-ET"))
        assertEquals(AppLanguage.OROMO, AppLanguage.fromTag("om"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("fr"))
    }

    @Test
    fun choiceLabelsTranslated() {
        for (k in ChoiceLabels.allKeys()) for (lang in AppLanguage.entries) {
            assertNotEquals(k, ChoiceLabels.label(k, lang))
        }
        TaskType.entries.flatMap { it.fields }.flatMap { it.choices }.forEach {
            assertTrue(it in ChoiceLabels.allKeys(), "Choice $it has no label")
        }
    }
}
