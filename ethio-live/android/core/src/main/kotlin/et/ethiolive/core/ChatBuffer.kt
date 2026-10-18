package et.ethiolive.core

/**
 * Keeps the newest [capacity] live-chat items ordered by server id, dropping duplicates. Realtime events,
 * snapshot reloads after a reconnect and our own optimistic sends can all deliver the same message.
 */
class ChatBuffer<T>(private val capacity: Int = 200, private val idOf: (T) -> Long) {
    private val byId = java.util.TreeMap<Long, T>()

    @Synchronized
    fun addAll(items: Iterable<T>): List<T> {
        for (item in items) byId[idOf(item)] = item
        while (byId.size > capacity) byId.pollFirstEntry()
        return byId.values.toList()
    }

    @Synchronized
    fun remove(id: Long): List<T> { byId.remove(id); return byId.values.toList() }

    @Synchronized
    fun clear() { byId.clear() }

    @Synchronized
    fun snapshot(): List<T> = byId.values.toList()
}
