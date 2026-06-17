package com.facegate.decision

import com.facegate.pipeline.AttendanceDecision
import com.facegate.pipeline.MatchCandidate
import com.facegate.pipeline.PipelineConfig
import com.facegate.pipeline.SimilarityMatch


class AttendanceDecisionEngine {

    companion object {
        
        val T_ACCEPT = PipelineConfig.THRESHOLD_ACCEPT      // 0.60
        val T_REJECT = PipelineConfig.THRESHOLD_REJECT      // 0.40

        const val AMBIGUITY_MARGIN = 0.08f
    }

    fun evaluate(
        match: SimilarityMatch,
        alreadyMarkedIds: Set<String> = emptySet(),
    ): AttendanceDecision {

        // Edge case: no templates enrolled 
        val top1 = match.topMatch
        if (top1 == null) {
            return AttendanceDecision.Reject(
                topSimilarity = 0f,
                reason = "No enrolled students found. Enroll students before taking attendance.",
            )
        }

        val top2 = match.secondMatch

        // 1. Already marked check

        if (top1.studentId in alreadyMarkedIds && top1.cosineSimilarity >= T_ACCEPT) {
            return AttendanceDecision.AlreadyMarked(
                studentId = top1.studentId,
                markedAt = System.currentTimeMillis(),
            )
        }

        // 2. Rejection check 

        if (top1.cosineSimilarity <= T_REJECT) {
            return AttendanceDecision.Reject(
                topSimilarity = top1.cosineSimilarity,
                reason = "Best match score ${fmt(top1.cosineSimilarity)} is below rejection threshold ${fmt(T_REJECT)}.",
            )
        }

        // 3. Ambiguity check — score in grey zone

        if (top1.cosineSimilarity < T_ACCEPT) {
            return AttendanceDecision.Ambiguous(
                topCandidate = top1,
                secondCandidate = top2,
                reason = "Score ${fmt(top1.cosineSimilarity)} is between T_reject (${fmt(T_REJECT)}) " +
                        "and T_accept (${fmt(T_ACCEPT)}). Manual review required.",
            )
        }

        // ── 4. Ambiguity check — two students too similar ────────

        if (top2 != null) {
            val margin = top1.cosineSimilarity - top2.cosineSimilarity
            if (margin < AMBIGUITY_MARGIN) {
                return AttendanceDecision.Ambiguous(
                    topCandidate = top1,
                    secondCandidate = top2,
                    reason = "Top-1 (${top1.studentName}: ${fmt(top1.cosineSimilarity)}) and " +
                            "top-2 (${top2.studentName}: ${fmt(top2.cosineSimilarity)}) " +
                            "are within ambiguity margin (${fmt(AMBIGUITY_MARGIN)}). " +
                            "Manual review required.",
                )
            }
        }

        // 5. Accept 
        
        return AttendanceDecision.Accept(
            studentId = top1.studentId,
            studentName = top1.studentName,
            confidence = top1.cosineSimilarity,
        )
    }

    private fun fmt(value: Float): String = String.format("%.3f", value)
}