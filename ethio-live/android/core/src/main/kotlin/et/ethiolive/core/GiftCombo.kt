package et.ethiolive.core

/**
 * Groups repeated sends of the same gift by the same sender into a combo ("Rose x5") for the overlay.
 * Purely visual: every send is still its own server transaction with its own idempotency key.
 */
class GiftCombo(private val windowMs: Long = 3_000) {
    private data class Key(val sender: String, val gift: String, val recipient: String)
    private data class Entry(var count: Int, var lastAt: Long)
    private val entries = HashMap<Key, Entry>()

    fun register(sender: String, gift: String, recipient: String, quantity: Int, nowMs: Long): Int {
        entries.entries.removeAll { nowMs - it.value.lastAt > windowMs }
        val e = entries.getOrPut(Key(sender, gift, recipient)) { Entry(0, nowMs) }
        e.count += quantity
        e.lastAt = nowMs
        return e.count
    }
}
