package com.facegate.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.facegate.alignment.FaceAligner
import com.facegate.decision.AttendanceDecisionEngine
import com.facegate.quality.QualityChecker
import com.facegate.recognition.FaceEmbedder
import com.facegate.similarity.EnrolledTemplate
import com.facegate.similarity.SimilaritySearch
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import java.util.PriorityQueue

/**
 * ATTENDANCE PIPELINE
 * ====================
 * The main orchestrator — wires all 8 stages together.
 *
 *   Camera frame
 *       -> [1] ML Kit face detection          ✅ real
 *       -> [2] Face count check               ✅ real
 *       -> [3] Quality checks                 ✅ real (QualityChecker)
 *       -> [4] Frame buffer / best frame      ✅ real
 *       -> [5] Face alignment                 ✅ real (FaceAligner + OpenCV)
 *       -> [6] MobileFaceNet embedding        ✅ real (FaceEmbedder + ONNX)
 *       -> [7] Cosine similarity search       ✅ real (SimilaritySearch)
 *       -> [8] Threshold decision             ✅ real (AttendanceDecisionEngine)
 *       -> DB write + sync                    ⏳ TODO (storage/ + sync/ not built yet)
 *
 * WHAT'S STILL TODO (storage/Database.kt not built yet):
 *   - Loading enrolled templates from SQLite at session start
 *   - Saving accepted attendance records to SQLite
 *   - Saving ambiguous cases to conflict queue in SQLite
 *   - Saving new student embeddings during enrollment
 *   - Triggering WorkManager sync after session ends
 *
 * WORKAROUND UNTIL STORAGE IS READY:
 *   enrolledTemplates is kept as an in-memory list.
 *   Templates added via enrollStudent() survive the session but are lost on app restart.
 *   Once Database.kt is built, replace these in-memory lists with
 *   repository calls (marked TODO below).
 */
class AttendancePipeline(
    private val context: Context,
    // TODO: inject TemplateRepository once storage/Database.kt is ready
    // private val repository: TemplateRepository,
) {

    // ── Component instances ──────────────────────────────────────────────────
    private val faceDetector    = buildFaceDetector()
    private val qualityChecker  = QualityChecker()
    private val faceAligner     = FaceAligner()
    private val faceEmbedder    = FaceEmbedder(context)
    private val similaritySearch = SimilaritySearch()
    private val decisionEngine  = AttendanceDecisionEngine()

    // ── Session state ────────────────────────────────────────────────────────
    private var sessionId: String? = null

    // Map of studentId -> timestamp when marked (needed by makeDecision's signature)
    private val alreadyMarkedMap = mutableMapOf<String, Long>()

    // ── In-memory template store (replaces DB until storage/ is ready) ───────
    // TODO: remove this once repository.loadAllTemplates() is implemented
    private val enrolledTemplates = mutableListOf<EnrolledTemplate>()

    // ── Frame buffer ─────────────────────────────────────────────────────────
    private data class BufferedFrame(
        val bitmap: Bitmap,
        val face: Face,
        val qualityScore: Float,
    )

    private val frameBuffer = PriorityQueue<BufferedFrame>(
        PipelineConfig.FRAME_BUFFER_SIZE,
        compareByDescending { it.qualityScore }
    )
    private var bufferingActive = false


    // ═════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Load the ONNX model and run a warmup inference.
     * Call once at app/ViewModel start — before any processFrame() call.
     */
    suspend fun init() {
        faceEmbedder.init()
        faceEmbedder.warmup()
    }

    /**
     * Begin an attendance session.
     * Loads enrolled templates into memory and starts frame collection.
     *
     * @param sessionId Unique identifier for this session (e.g. "CLASS_A_2026-06-19")
     */
    suspend fun startSession(sessionId: String) {
        this.sessionId = sessionId
        alreadyMarkedMap.clear()
        startFrameBuffering()

        // TODO: replace these two lines with DB load once storage/ is ready:
        // val dbTemplates = repository.loadAllTemplates()
        // enrolledTemplates.clear()
        // enrolledTemplates.addAll(dbTemplates)
        //
        // For now, enrolledTemplates already holds whatever was enrolled
        // in this app session — no-op here until storage is wired.
    }

    /**
     * End the session. Clears biometric data from memory immediately (security).
     * Call when teacher taps "End Attendance".
     */
    fun endSession() {
        alreadyMarkedMap.clear()
        frameBuffer.clear()
        bufferingActive = false
        sessionId = null

        // TODO: trigger AttendanceSyncWorker.triggerNow() once sync/ is ready
    }

    /**
     * Release all resources. Call from ViewModel.onCleared() or Activity.onDestroy().
     */
    fun destroy() {
        endSession()
        faceDetector.close()
        faceEmbedder.close()
    }


    // ═════════════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT — called from CameraX ImageAnalysis
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Process one camera frame through the full 8-stage pipeline.
     *
     * Call this from CameraX's ImageAnalysis.Analyzer at ~10fps.
     * Returns a PipelineFrameStatus that the UI observes via StateFlow.
     *
     * All 8 stages are now real and wired. The only limitation is that
     * enrolled templates come from in-memory storage rather than SQLite
     * (since storage/ isn't built yet). Once it's ready, add the
     * TODO DB calls in startSession() and handleDecision().
     *
     * @param bitmap Camera preview frame (any resolution)
     * @return PipelineFrameStatus for the UI to render
     */
    suspend fun processFrame(bitmap: Bitmap): PipelineFrameStatus {
        sessionId ?: return PipelineFrameStatus.NoSession
        val t0 = SystemClock.elapsedRealtime()

        // ── Stage 1: ML Kit Face Detection ───────────────────────────────────
        val tDetectStart = SystemClock.elapsedRealtime()
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = faceDetector.process(image).await()
        val detectionMs = SystemClock.elapsedRealtime() - tDetectStart

        // ── Stage 2: Face Count Check ─────────────────────────────────────────
        val detectedFace: Face = when (faces.size) {
            0    -> return PipelineFrameStatus.NoFace
            1    -> faces[0]
            else -> return PipelineFrameStatus.MultipleFaces
        }

        // ── Stage 3: Quality Check ────────────────────────────────────────────
        val tQualityStart = SystemClock.elapsedRealtime()
        val quality = qualityChecker.check(bitmap, detectedFace)
        val qualityMs = SystemClock.elapsedRealtime() - tQualityStart

        if (!quality.passed) {
            return PipelineFrameStatus.QualityFailed(quality.failReasons)
        }

        // ── Stage 4: Frame Buffer ─────────────────────────────────────────────
        bufferFrame(bitmap, detectedFace, quality.qualityScore)

        if (!isBufferReady()) {
            return PipelineFrameStatus.Buffering(
                framesCollected = frameBuffer.size,
                framesNeeded    = PipelineConfig.FRAME_BUFFER_SIZE,
            )
        }

        // Buffer is full — pop the best-quality frame and reset for next person
        val bestFrame = frameBuffer.peek()!!
        frameBuffer.clear()
        startFrameBuffering()

        // ── Stage 5: Face Alignment ───────────────────────────────────────────
        val tAlignStart = SystemClock.elapsedRealtime()
        val alignmentResult = faceAligner.align(bestFrame.bitmap, bestFrame.face)
        val alignmentMs = SystemClock.elapsedRealtime() - tAlignStart

        // FaceAligner returns AlignmentResult(alignedBitmap, landmarksFound)
        // FaceEmbedder.embed() expects AlignedFace(bitmap, sourceFrame) from PipelineModels
        val alignedFace = AlignedFace(
            bitmap      = alignmentResult.alignedBitmap,
            sourceFrame = bestFrame.bitmap,
        )

        // ── Stage 6: Face Embedding ───────────────────────────────────────────
        val embedding = faceEmbedder.embed(alignedFace)
        val inferenceMs = embedding.inferenceTimeMs

        // ── Stage 7: Cosine Similarity Search ────────────────────────────────
        val tSearchStart = SystemClock.elapsedRealtime()
        val match = similaritySearch.search(embedding, enrolledTemplates)
        val similarityMs = SystemClock.elapsedRealtime() - tSearchStart

        // ── Stage 8: Threshold Decision ───────────────────────────────────────
        val decision = decisionEngine.makeDecision(match, alreadyMarkedMap)

        val totalMs = SystemClock.elapsedRealtime() - t0

        // ── Handle decision side-effects ──────────────────────────────────────
        handleDecision(decision)

        // ── Build final result and return to UI ───────────────────────────────
        val result = PipelineResult(
            decision     = decision,
            detectionMs  = detectionMs,
            qualityMs    = qualityMs,
            alignmentMs  = alignmentMs,
            inferenceMs  = inferenceMs,
            similarityMs = similarityMs,
            totalMs      = totalMs,
        )

        return PipelineFrameStatus.Decision(result)
    }


    // ═════════════════════════════════════════════════════════════════════════
    // ENROLLMENT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Enroll a new student. Takes a photo, runs the full pipeline to generate
     * their face embedding, checks for duplicates, and saves the template.
     *
     * Currently saves to in-memory list only (survives session, not app restart).
     * Once storage/ is built, replace the in-memory save with repository.saveTemplate().
     *
     * @param studentId     Unique ID (e.g. roll number)
     * @param studentName   Display name shown on accept screen
     * @param studentClass  Class/section label
     * @param photo         Clean frontal photo (any resolution)
     * @return EnrollmentResult — Success / NoFaceDetected / MultipleFacesDetected /
     *         QualityFailed / DuplicateRisk
     */
    suspend fun enrollStudent(
        studentId: String,
        studentName: String,
        studentClass: String,
        photo: Bitmap,
    ): EnrollmentResult {

        // ── Detect face in photo ──────────────────────────────────────────────
        val image = InputImage.fromBitmap(photo, 0)
        val faces = faceDetector.process(image).await()

        when (faces.size) {
            0    -> return EnrollmentResult.NoFaceDetected
            1    -> { /* continue */ }
            else -> return EnrollmentResult.MultipleFacesDetected
        }

        val detectedFace = faces[0]

        // ── Quality check ─────────────────────────────────────────────────────
        val quality = qualityChecker.check(photo, detectedFace)
        if (!quality.passed) {
            return EnrollmentResult.QualityFailed(quality.failReasons)
        }

        // ── Align + embed ─────────────────────────────────────────────────────
        val alignmentResult = faceAligner.align(photo, detectedFace)
        val alignedFace = AlignedFace(
            bitmap      = alignmentResult.alignedBitmap,
            sourceFrame = photo,
        )
        val embedding = faceEmbedder.embed(alignedFace)

        // ── Duplicate check ───────────────────────────────────────────────────
        val duplicate = similaritySearch.findDuplicateRisk(embedding, enrolledTemplates)
        if (duplicate != null) {
            return EnrollmentResult.DuplicateRisk(
                existingStudentId   = duplicate.studentId,
                existingStudentName = duplicate.studentName,
            )
        }

        // ── Save template ─────────────────────────────────────────────────────
        // In-memory save — works for this session only
        enrolledTemplates.add(
            EnrolledTemplate(
                studentId   = studentId,
                studentName = studentName,
                embedding   = embedding.vector,
            )
        )

        // TODO: replace in-memory save above with DB write once storage/ is ready:
        // repository.saveTemplate(
        //     studentId    = studentId,
        //     studentName  = studentName,
        //     studentClass = studentClass,
        //     embedding    = embedding.vector,
        // )

        return EnrollmentResult.Success
    }

    /**
     * Returns the number of students currently enrolled in memory.
     * Useful for the admin dashboard to show enrollment count.
     */
    fun enrolledCount(): Int = enrolledTemplates.size

    /**
     * Returns all enrolled student IDs and names (no embeddings — safe to expose to UI).
     */
    fun enrolledStudents(): List<Pair<String, String>> =
        enrolledTemplates.map { it.studentId to it.studentName }


    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Handle side-effects of a decision:
     * - Accept: record the student as marked in this session
     * - Ambiguous: should go to conflict queue (TODO: DB write)
     * - Reject / AlreadyMarked: no state change needed
     */
    private fun handleDecision(decision: AttendanceDecision) {
        when (decision) {
            is AttendanceDecision.Accept -> {
                alreadyMarkedMap[decision.studentId] = System.currentTimeMillis()

                // TODO: once storage/ is ready, write to DB:
                // repository.markAttendance(
                //     studentId  = decision.studentId,
                //     sessionId  = sessionId!!,
                //     markedAt   = alreadyMarkedMap[decision.studentId]!!,
                //     confidence = decision.confidence,
                //     method     = "AUTO"
                // )
            }

            is AttendanceDecision.Ambiguous -> {
                // TODO: once storage/ is ready, save to conflict queue:
                // repository.saveConflict(
                //     sessionId      = sessionId!!,
                //     topStudentId   = decision.topCandidate?.studentId,
                //     topStudentName = decision.topCandidate?.studentName,
                //     reason         = decision.reason,
                // )
            }

            is AttendanceDecision.Reject,
            is AttendanceDecision.AlreadyMarked -> {
                // No side-effects needed
            }
        }
    }

    /**
     * Configure and create the ML Kit face detector.
     * Accurate mode + all landmarks (needed for FaceAligner's 5-point warp).
     */
    private fun buildFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        return FaceDetection.getClient(options)
    }

    private fun startFrameBuffering() {
        frameBuffer.clear()
        bufferingActive = true
    }

    private fun bufferFrame(bitmap: Bitmap, face: Face, qualityScore: Float) {
        if (!bufferingActive) return
        if (frameBuffer.size >= PipelineConfig.FRAME_BUFFER_SIZE) return
        frameBuffer.add(BufferedFrame(bitmap, face, qualityScore))
    }

    private fun isBufferReady(): Boolean =
        frameBuffer.size >= PipelineConfig.FRAME_BUFFER_SIZE
}