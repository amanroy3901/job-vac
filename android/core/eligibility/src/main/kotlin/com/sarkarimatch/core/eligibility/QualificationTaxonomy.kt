package com.sarkarimatch.core.eligibility

/**
 * Qualification codes form a tree ("grad.ba" → parent "grad.any") with numeric levels. The real taxonomy ships in
 * the ReferenceBundle (`GET /v1/reference`); [Builtin] is a small subset for tests and first-run fallback.
 */
interface QualificationTaxonomy {
    fun level(code: String): Int?
    fun parent(code: String): String?

    /** The code itself and all its ancestors. */
    fun selfAndAncestors(code: String): List<String> =
        generateSequence(code) { parent(it) }.take(MAX_DEPTH).toList()

    data class Node(val code: String, val parent: String?, val level: Int)

    class Simple(nodes: List<Node>) : QualificationTaxonomy {
        private val byCode = nodes.associateBy { it.code }
        override fun level(code: String) = byCode[code]?.level
        override fun parent(code: String) = byCode[code]?.parent
    }

    companion object {
        private const val MAX_DEPTH = 8

        /** Levels: 10 = class 10, 20 = class 12, 25 = diploma/ITI, 40 = graduate, 50 = post-graduate. */
        val Builtin: QualificationTaxonomy = Simple(
            listOf(
                Node("class8", null, 8),
                Node("class10", null, 10),
                Node("iti", null, 15),
                Node("class12", null, 20),
                Node("class12.science", "class12", 20),
                Node("diploma.any", null, 25),
                Node("diploma.engg", "diploma.any", 25),
                Node("grad.any", null, 40),
                Node("grad.ba", "grad.any", 40),
                Node("grad.bsc", "grad.any", 40),
                Node("grad.bcom", "grad.any", 40),
                Node("grad.btech", "grad.any", 40),
                Node("grad.bed", "grad.any", 40),
                Node("pg.any", null, 50),
                Node("pg.ma", "pg.any", 50),
                Node("pg.msc", "pg.any", 50),
            ),
        )
    }
}
