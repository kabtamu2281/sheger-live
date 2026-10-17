package et.bizassistant.core

/** Something that can appear in the saved-outputs library. */
data class SearchableDocument(val id: Long, val title: String, val body: String, val kind: String, val createdAtEpochMs: Long)

/** Case-insensitive, all-terms search over saved documents, newest first. Works for Ethiopic script too. */
object LibrarySearch {
    fun search(docs: List<SearchableDocument>, query: String): List<SearchableDocument> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val filtered = if (terms.isEmpty()) docs else docs.filter { d ->
            val hay = (d.title + "\n" + d.body + "\n" + d.kind).lowercase()
            terms.all { hay.contains(it) }
        }
        return filtered.sortedByDescending { it.createdAtEpochMs }
    }
}
