package et.ethiolive.core

import java.time.LocalDate
import java.time.Period

/** Client-side checks that mirror the server's rules, for instant feedback. The server remains authoritative. */
object Validation {
    const val MIN_SIGNUP_AGE = 16
    const val ADULT_AGE = 18
    const val CHAT_MAX = 300
    const val DM_MAX = 1000
    const val TITLE_MAX = 60
    const val BIO_MAX = 300

    private val username = Regex("^[a-zA-Z0-9_.]{3,24}$")
    private val reserved = Regex("(ethio_?live|official|admin|support|moderator)", RegexOption.IGNORE_CASE)
    private val e164 = Regex("^\\+[1-9][0-9]{7,14}$")
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun usernameProblem(value: String): ServerError? = when {
        !username.matches(value) -> ServerError.USERNAME_INVALID
        reserved.containsMatchIn(value) -> ServerError.USERNAME_RESERVED
        else -> null
    }

    fun age(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years

    fun signupAgeProblem(birthDate: LocalDate, today: LocalDate): ServerError? = when {
        birthDate.isAfter(today) || age(birthDate, today) > 120 -> ServerError.INVALID_BIRTH_DATE
        age(birthDate, today) < MIN_SIGNUP_AGE -> ServerError.AGE_INELIGIBLE
        else -> null
    }

    fun isAdult(birthDate: LocalDate, today: LocalDate) = age(birthDate, today) >= ADULT_AGE

    /**
     * Normalises Ethiopian numbers to E.164: "0911 234 567" / "911234567" / "+251911234567" -> "+251911234567".
     * Numbers that already carry another country code are kept as typed.
     */
    fun normalizePhone(input: String, defaultCountryCode: String = "251"): String? {
        val digits = input.filter { it.isDigit() || it == '+' }
        val candidate = when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.startsWith(defaultCountryCode) && digits.length >= 11 -> "+$digits"
            digits.startsWith("0") -> "+$defaultCountryCode" + digits.drop(1)
            digits.length == 9 -> "+$defaultCountryCode$digits"
            else -> "+$digits"
        }
        return candidate.takeIf { e164.matches(it) }
    }

    fun isEmail(value: String) = email.matches(value.trim())

    fun passwordProblem(value: String): String? = when {
        value.length < 8 -> "too_short"
        value.none { it.isDigit() } || value.none { it.isLetter() } -> "needs_letters_and_digits"
        else -> null
    }

    fun chatMessage(value: String): String? = value.trim().takeIf { it.isNotEmpty() && it.length <= CHAT_MAX }

    fun hashtags(raw: String): List<String> =
        raw.split(' ', ',', '\n').map { it.trim().trimStart('#').lowercase() }
            .filter { it.matches(Regex("^[\\p{L}\\p{N}_]{1,30}$")) }.distinct().take(5)
}
