package com.facegate.ui.enrollment

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.pipeline.AttendancePipeline
import com.facegate.pipeline.CaptureQualityResult
import com.facegate.pipeline.CaptureRejectReason
import com.facegate.pipeline.EnrollmentResult
import com.facegate.pipeline.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// All possible states of the enrollment screen.
 
sealed class EnrollmentState {
    object Idle          : EnrollmentState()
    object Capturing     : EnrollmentState()
    object Processing    : EnrollmentState()
    object Success       : EnrollmentState()
    object DuplicateFace : EnrollmentState()
    data class Failed(val reason: String = "Please try again") : EnrollmentState()
    data class ShotRejected(val reason: String) : EnrollmentState()
}

// ENROLLMENT VIEWMODEL — per-shot quality gate + averaged embedding

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val pipeline: AttendancePipeline,
) : ViewModel() {

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState

    private val verifiedBitmaps = mutableListOf<Bitmap>()

    // ── Photo capture ────────────────────────────────────────────────────────

    fun capturePhoto(bitmap: Bitmap, rotationDegrees: Int = 0): Boolean {

        viewModelScope.launch {
            val result = pipeline.checkCaptureQuality(bitmap, rotationDegrees)
            when (result) {
                is CaptureQualityResult.Pass -> {
                    verifiedBitmaps.add(result.bitmap)
                    _enrollmentState.value = EnrollmentState.Capturing
                    if (verifiedBitmaps.size >= 5) {
                        // Signal Fragment to show the student-info dialog
                        _enrollmentState.value = EnrollmentState.Processing
                    }
                }
                is CaptureQualityResult.Fail -> {
                    val reason = when (result.reason) {
                        CaptureRejectReason.NO_FACE        -> "No face detected — look at the camera"
                        CaptureRejectReason.MULTIPLE_FACES -> "Multiple faces detected — only one person should be in frame"
                        CaptureRejectReason.QUALITY        -> result.failDetail.toUserMessage()
                    }
                    _enrollmentState.value = EnrollmentState.ShotRejected(reason)
                    // Return to Capturing state so the button stays enabled
                    _enrollmentState.value = EnrollmentState.Capturing
                    // Recycle the raw bitmap — we won't use it
                    bitmap.recycle()
                }
            }
        }
        // Return current count status (Fragment uses the state flow for actual UI updates)
        return verifiedBitmaps.size >= 5
    }

    fun capturedCount(): Int = verifiedBitmaps.size

    // ── Enrollment ───────────────────────────────────────────────────────────

    fun enrollStudent(
        studentName : String,
        studentId   : String,
        studentClass: String,
    ) {
        if (verifiedBitmaps.isEmpty()) {
            _enrollmentState.value = EnrollmentState.Failed("No photos captured. Please take photos first.")
            return
        }

        viewModelScope.launch {
            _enrollmentState.value = EnrollmentState.Processing

            val result = try {
                pipeline.enrollStudentFromEmbeddings(
                    studentId       = studentId,
                    studentName     = studentName,
                    studentClass    = studentClass,
                    verifiedBitmaps = verifiedBitmaps.toList(),
                )
            } catch (e: Exception) {
                clearBitmaps()
                _enrollmentState.value = EnrollmentState.Failed(
                    e.message ?: "Enrollment failed — please try again"
                )
                return@launch
            }

            clearBitmaps()

            _enrollmentState.value = when (result) {
                is EnrollmentResult.Success        -> EnrollmentState.Success
                is EnrollmentResult.DuplicateRisk  -> EnrollmentState.DuplicateFace
                is EnrollmentResult.NoFaceDetected -> EnrollmentState.Failed(
                    "No face detected in photos. Please retake with good lighting."
                )
                is EnrollmentResult.MultipleFacesDetected -> EnrollmentState.Failed(
                    "Multiple faces detected. Only one person should be in frame."
                )
                is EnrollmentResult.QualityFailed  -> EnrollmentState.Failed(
                    result.reasons.toUserMessage()
                )
            }
        }
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    fun reset() {
        clearBitmaps()
        _enrollmentState.value = EnrollmentState.Idle
    }

    private fun clearBitmaps() {
        verifiedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        verifiedBitmaps.clear()
    }

    override fun onCleared() {
        super.onCleared()
        clearBitmaps()
    }
}