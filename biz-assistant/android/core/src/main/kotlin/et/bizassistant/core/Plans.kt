package et.bizassistant.core

import java.time.LocalDate

/**
 * Plan catalogue. The backend is the source of truth; this mirrors its defaults for offline display.
 * Prices are placeholders to be set by the operator before launch.
 */
enum class PlanId { FREE, STARTER, BUSINESS }

data class Plan(val id: PlanId, val name: String, val monthlyAiDrafts: Int, val monthlyPrice: Money)

data class Bundle(val id: String, val name: String, val extraAiDrafts: Int, val price: Money)

object PlanCatalog {
    val plans = listOf(
        Plan(PlanId.FREE, "Free", 20, Money.ZERO),
        Plan(PlanId.STARTER, "Starter", 200, Money.ofMajor(149)),
        Plan(PlanId.BUSINESS, "Business", 1000, Money.ofMajor(449)),
    )
    val bundles = listOf(
        Bundle("bundle_100", "Business pack 100", 100, Money.ofMajor(79)),
        Bundle("bundle_500", "Business pack 500", 500, Money.ofMajor(299)),
    )

    fun plan(id: PlanId) = plans.first { it.id == id }
}

/** The user's AI allowance for the current period, as reported by the backend. */
data class UsageQuota(
    val plan: PlanId,
    val used: Int,
    val limit: Int,
    val bonusRemaining: Int = 0,
    val resetsOn: LocalDate,
) {
    val remaining: Int get() = (limit - used).coerceAtLeast(0) + bonusRemaining
    val isExhausted: Boolean get() = remaining <= 0
    val totalAllowance: Int get() = limit + bonusRemaining
}

/** Explicit, revocable permission to send user content to the external AI service. */
data class ConsentState(val aiAllowed: Boolean, val decidedAtEpochMs: Long?) {
    companion object {
        val UNDECIDED = ConsentState(aiAllowed = false, decidedAtEpochMs = null)
    }
}
