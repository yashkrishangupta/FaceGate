package com.facegate.similarity

import com.facegate.pipeline.MatchCandidate
import com.facegate.pipeline.SimilarityMatch

class SimilaritySearch {

    private val templates = mutableMapOf<String, EnrolledTemplate>()

    data class EnrolledTemplate(
        val studentId: String,
        val studentName: String,
        val embedding: FloatArray,   // 128-D, L2-normalized
    ) {
        override fun equals(other: Any?) = other is EnrolledTemplate && studentId == other.studentId
        override fun hashCode() = studentId.hashCode()
    }

    // Template management

    fun loadTemplates(enrolled: List<EnrolledTemplate>) {
        templates.clear()
        enrolled.forEach { templates[it.studentId] = it }
    }

    fun clearTemplates() {
        templates.clear()
    }

    val enrolledCount: Int get() = templates.size


    // Search

    fun search(probe: FloatArray): SimilarityMatch {
        val t0 = System.currentTimeMillis()

        if (templates.isEmpty()) {
            return SimilarityMatch(
                topMatch = null,
                secondMatch = null,
                searchTimeMs = System.currentTimeMillis() - t0,
            )
        }

        var top1: MatchCandidate? = null
        var top2: MatchCandidate? = null

        for ((_, template) in templates) {
            val score = dotProduct(probe, template.embedding)

            if (top1 == null || score > top1.cosineSimilarity) {
                top2 = top1
                top1 = MatchCandidate(
                    studentId = template.studentId,
                    studentName = template.studentName,
                    cosineSimilarity = score,
                )
            } else if (top2 == null || score > top2.cosineSimilarity) {
                top2 = MatchCandidate(
                    studentId = template.studentId,
                    studentName = template.studentName,
                    cosineSimilarity = score,
                )
            }
        }

        return SimilarityMatch(
            topMatch = top1,
            secondMatch = top2,
            searchTimeMs = System.currentTimeMillis() - t0,
        )
    }


    // Enrollment duplicate check

    fun checkDuplicateRisk(
        newEmbedding: FloatArray,
        warningThreshold: Float = 0.70f,
    ): EnrolledTemplate? {
        var maxScore = Float.MIN_VALUE
        var closestMatch: EnrolledTemplate? = null

        for ((_, template) in templates) {
            val score = dotProduct(newEmbedding, template.embedding)
            if (score > maxScore) {
                maxScore = score
                closestMatch = template
            }
        }

        return if (maxScore >= warningThreshold) closestMatch else null
    }


    // Core math

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var sum = 0f
        val len = a.size
        var i = 0
        while (i <= len - 4) {
            sum += a[i] * b[i] + a[i + 1] * b[i + 1] + a[i + 2] * b[i + 2] + a[i + 3] * b[i + 3]
            i += 4
        }
        while (i < len) {
            sum += a[i] * b[i]
            i++
        }
        return sum
    }
}