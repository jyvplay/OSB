package dev.fusion.index

/**
 * Bounded faceted search over documents with filter categories.
 *
 * Each document has an id and a map of facet name -> set of values.
 * Queries combine facet filters (AND across facets, OR within a facet)
 * with an optional full-text term filter.
 *
 * Bounds (per call):
 *   - documents <= 100,000
 *   - facets per document <= 64
 *   - values per facet <= 256
 *   - facet name length <= 128 UTF-16 code units
 *   - facet value length <= 512 UTF-16 code units
 *   - query terms <= 64
 *   - result set capped at 10,000
 *
 * Deterministic: results are returned sorted by document id.
 */
object FacetedSearch {
    private const val MAX_DOCUMENTS = 100_000
    private const val MAX_FACETS_PER_DOC = 64
    private const val MAX_VALUES_PER_FACET = 256
    private const val MAX_FACET_NAME = 128
    private const val MAX_FACET_VALUE = 512
    private const val MAX_QUERY_TERMS = 64
    private const val MAX_RESULTS = 10_000

    data class Document(
        val id: String,
        val facets: Map<String, Set<String>>,
        val text: String
    )

    data class FacetFilter(
        val facet: String,
        val values: Set<String> // OR within this facet
    )

    data class Query(
        val facetFilters: List<FacetFilter>, // AND across facets
        val term: String? = null
    )

    data class Result(
        val documentIds: List<String>,
        val totalScanned: Int,
        val totalMatching: Int
    )

    private val documents = mutableMapOf<String, Document>()

    fun addDocument(doc: Document) {
        require(documents.size < MAX_DOCUMENTS) { "Too many documents ($MAX_DOCUMENTS max)" }
        require(doc.id.isNotEmpty() && doc.id.length <= 256) { "Invalid document id" }
        require(doc.facets.size <= MAX_FACETS_PER_DOC) { "Too many facets per document" }
        for ((name, values) in doc.facets) {
            require(name.length <= MAX_FACET_NAME) { "Facet name too long" }
            require(values.size <= MAX_VALUES_PER_FACET) { "Too many values per facet" }
            for (v in values) {
                require(v.length <= MAX_FACET_VALUE) { "Facet value too long" }
            }
        }
        require(doc.text.length <= 65_536) { "Document text too long" }
        documents[doc.id] = doc
    }

    fun search(query: Query): Result {
        require(query.facetFilters.size <= 64) { "Too many facet filters" }
        for (ff in query.facetFilters) {
            require(ff.facet.length <= MAX_FACET_NAME) { "Facet name too long" }
            require(ff.values.size <= MAX_VALUES_PER_FACET) { "Too many filter values" }
        }
        require(query.term == null || query.term.length <= 512) { "Query term too long" }

        val term = query.term?.lowercase()
        val matching = mutableListOf<String>()

        for ((id, doc) in documents) {
            // AND across facets: every facet filter must match
            var allFacetsMatch = true
            for (ff in query.facetFilters) {
                val docValues = doc.facets[ff.facet] ?: emptySet()
                if (!docValues.intersect(ff.values).isNotEmpty()) {
                    allFacetsMatch = false
                    break
                }
            }
            if (!allFacetsMatch) continue

            // Term filter: case-insensitive substring match
            if (term != null && !doc.text.lowercase().contains(term)) continue

            matching.add(id)
            if (matching.size >= MAX_RESULTS) break
        }

        matching.sort()

        return Result(
            documentIds = matching,
            totalScanned = documents.size,
            totalMatching = matching.size
        )
    }

    fun documentCount(): Int = documents.size

    fun clear() { documents.clear() }

    fun facetNames(): List<String> {
        val names = mutableSetOf<String>()
        for (doc in documents.values) {
            names += doc.facets.keys
        }
        return names.sorted()
    }

    fun facetValues(facet: String): List<String> {
        val values = mutableSetOf<String>()
        for (doc in documents.values) {
            doc.facets[facet]?.let { values += it }
        }
        return values.sorted()
    }
}
