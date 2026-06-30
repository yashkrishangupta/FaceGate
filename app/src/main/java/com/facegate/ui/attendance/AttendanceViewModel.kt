package com.facegate.ui.attendance

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.pipeline.AttendanceDecision
import com.facegate.pipeline.AttendancePipeline
import com.facegate.pipeline.PipelineFrameStatus
import com.facegate.pipeline.QualityFailReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * All possible states of the attendance camera screen.
 */
sealed class ScanState {
    object Idle : ScanState()

    /** message defaults to the generic copy so existing call sites without a reason still work. */
    data class Scanning(val message: String = "Hold still — scanning…") : ScanState()

    object Processing : ScanState()
    data class Success(
        val studentName  : String,
        val studentClass : String,
        val initials     : String,
        val markedTime   : String,
    ) : ScanState()

    /** title/message default to the old generic copy so nothing else breaks. */
    data class Failed(
        val title  : String = "Face Not Recognized",
        val message: String = "Please try again",
    ) : ScanState()
}

/**
 * ATTENDANCE VIEWMODEL — fully wired to real pipeline
 */
@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val pipeline: AttendancePipeline,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private val _windowCountdown = MutableStateFlow(0L)
    val windowCountdown: StateFlow<Long> = _windowCountdown

    // Prevents queuing up multiple processFrame calls while one ML inference is running.
    // ML inference takes 150-400ms; camera delivers frames faster than that.
    @Volatile
    private var isProcessing = false

    // Paused during Success/Failed so the result stays on screen for the full
    // display duration instead of being overwritten by the next camera frame.
    @Volatile
    private var isPaused = false

    // ── Session lifecycle ────────────────────────────────────────────────────

    /**
     * Start an attendance session. sessionId now comes from TodayScheduleFragment,
     * not generated here.
     * Loads enrolled students from DB into memory for the pipeline.
     */
    fun startSession(sessionId: String, subject: String, batch: String, windowMinutes: Int) {
        viewModelScope.launch {
            try { pipeline.init() } catch (_: Exception) {}
            pipeline.startSession(sessionId, windowMinutes)
            _scanState.value = ScanState.Scanning("Ready — show your face")
            startCountdownTimer(windowMinutes)
        }
    }

    // Tracks the running countdown coroutine so it can be cancelled on stopSession()
    private var countdownJob: Job? = null

    /**
     * End the session. Call from Fragment.onPause() or "End Attendance" button.
     * Clears biometric data from memory.
     */
    fun stopSession() {
        countdownJob?.cancel()
        pipeline.endSession()
        _scanState.value = ScanState.Idle
    }

    // ── Main frame processing ────────────────────────────────────────────────

    fun processFrame(bitmap: Bitmap, rotationDegrees: Int = 0) {
        if (isProcessing || isPaused) return
        isProcessing = true

        viewModelScope.launch {
            try {
                val status = pipeline.processFrame(bitmap, rotationDegrees)
                _scanState.value = mapStatusToScanState(status)
            } catch (e: Exception) {
                e.printStackTrace()
                _scanState.value = ScanState.Idle
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Map PipelineFrameStatus (pipeline layer) → ScanState (UI layer).
     */
    private fun mapStatusToScanState(status: PipelineFrameStatus): ScanState {
        return when (status) {
            is PipelineFrameStatus.NoSession      -> ScanState.Idle
            is PipelineFrameStatus.NoFace         -> ScanState.Idle
            is PipelineFrameStatus.MultipleFaces  ->
                ScanState.Scanning("Multiple faces detected — only one person at a time")
            is PipelineFrameStatus.QualityFailed  -> ScanState.Scanning(status.reasons.toDisplayMessage())
            is PipelineFrameStatus.Buffering      -> ScanState.Scanning()
            is PipelineFrameStatus.Processing     -> ScanState.Processing
            is PipelineFrameStatus.Decision       -> {
                when (val d = status.result.decision) {

                    is AttendanceDecision.Accept -> {
                        isPaused = true
                        ScanState.Success(
                            studentName  = d.studentName,
                            studentClass = "Confidence: ${(d.confidence * 100).toInt()}%",
                            initials     = d.studentName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString(""),
                            markedTime   = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(Date()),
                        )
                    }

                    is AttendanceDecision.AlreadyMarked -> {
                        isPaused = true
                        ScanState.Success(
                            studentName  = d.studentId,
                            studentClass = "Already marked",
                            initials     = "--",
                            markedTime   = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(Date(d.markedAt)),
                        )
                    }

                    is AttendanceDecision.Reject -> {
                        isPaused = true
                        ScanState.Failed(
                            title   = "Not Recognized",
                            message = d.reason,
                        )
                    }

                    is AttendanceDecision.Ambiguous -> {
                        isPaused = true
                        ScanState.Failed(
                            title   = "Needs Admin Review",
                            message = "${d.reason} Flagged for admin review.",
                        )
                    }
                }
            }
        }
    }

    /** Friendly, actionable copy for each quality failure reason. */
    private fun QualityFailReason.toMessage(): String = when (this) {
        QualityFailReason.TOO_BLURRY              -> "Image too blurry — hold the camera steady"
        QualityFailReason.TOO_DARK                -> "Too dark — move to better lighting"
        QualityFailReason.TOO_BRIGHT              -> "Too bright — reduce glare or backlight"
        QualityFailReason.FACE_TOO_SMALL          -> "Move closer to the camera"
        QualityFailReason.HEAD_TURNED_YAW         -> "Face the camera directly"
        QualityFailReason.HEAD_TILTED_PITCH       -> "Keep your head level"
        QualityFailReason.HEAD_ROTATED_ROLL       -> "Straighten your head — don't tilt sideways"
        QualityFailReason.LOW_LANDMARK_CONFIDENCE -> "Can't see your face clearly — adjust position"
    }

    private fun List<QualityFailReason>.toDisplayMessage(): String =
        firstOrNull()?.toMessage() ?: "Hold still — scanning…"

    /**
     * Ticks every second while the session's attendance window is open.
     * Updates _windowCountdown with remaining ms and flips scan state to a
     * "window closed" message once the window expires.
     */
    private fun startCountdownTimer(windowMinutes: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = pipeline.remainingWindowMs()
                _windowCountdown.value = remaining
                if (remaining <= 0L) {
                    _scanState.value = ScanState.Failed(
                        title   = "Window closed",
                        message = "Window closed — late marks go to review",
                    )
                    break
                }
                delay(1000L)
            }
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    fun resetScan() {
        isPaused = false
        _scanState.value = ScanState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        pipeline.endSession()
    }
}