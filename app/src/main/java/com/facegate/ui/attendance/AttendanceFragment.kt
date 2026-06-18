package com.facegate.ui.attendance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.facegate.R
import com.facegate.databinding.FragmentAttendanceBinding

/**
 * ATTENDANCE FRAGMENT
 * Camera screen with face oval and scan simulation
 */
class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AttendanceViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    // ── LIFECYCLE ────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        resetToIdle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearScan()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        clearScan()
    }

    override fun onResume() {
        super.onResume()
        resetToIdle()
    }

    // ── SCAN STATE MACHINE ───────────────────────────

    private fun resetToIdle() {
        clearScan()
        binding.faceOval.setImageResource(R.drawable.oval_face_guide)
        binding.tvScanBadge.text  = "Searching…"
        binding.tvStatusLabel.text = "READY"
        binding.tvStatusMain.text  = "Place your face inside the oval"
        binding.tvStatusSub.text   = "Keep your face centered and look straight"
        binding.processingDots.visibility = View.GONE
        binding.scanLine.visibility = View.GONE

        scanRunnable = Runnable { showScanningState() }
        handler.postDelayed(scanRunnable!!, 1400)
    }

    private fun showScanningState() {
        binding.faceOval.setImageResource(R.drawable.oval_face_scanning)
        binding.tvScanBadge.text   = "Face detected"
        binding.tvStatusLabel.text = "SCANNING"
        binding.tvStatusMain.text  = "Hold still — scanning…"
        binding.tvStatusSub.text   = "Analyzing facial features"
        binding.scanLine.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(
            requireContext(), R.anim.scan_line
        )
        binding.scanLine.startAnimation(anim)
    }

    private fun triggerScan() {
        clearScan()
        binding.faceOval.setImageResource(R.drawable.oval_face_scanning)
        binding.tvScanBadge.text   = "Processing…"
        binding.tvStatusLabel.text = "PROCESSING"
        binding.tvStatusMain.text  = "Identifying student…"
        binding.tvStatusSub.text   = "Matching against database"
        binding.processingDots.visibility = View.VISIBLE
        binding.scanLine.visibility = View.GONE
        animateProcessingDots()

        val succeed = Math.random() > 0.35

        scanRunnable = Runnable {
            if (succeed) {
                showSuccessState()
            } else {
                showFailState()
            }
        }
        handler.postDelayed(scanRunnable!!, 2200)
    }

    private fun showSuccessState() {
        binding.faceOval.setImageResource(R.drawable.oval_face_success)
        binding.tvScanBadge.text   = "Recognized ✓"
        binding.tvStatusLabel.text = "SUCCESS"
        binding.tvStatusMain.text  = "Attendance Marked!"
        binding.tvStatusSub.text   = "Arjun Kumar — Class 9-B"
        binding.processingDots.visibility = View.GONE
    }

    private fun showFailState() {
        binding.faceOval.setImageResource(R.drawable.oval_face_fail)
        binding.tvScanBadge.text   = "Not Recognized"
        binding.tvStatusLabel.text = "FAILED"
        binding.tvStatusMain.text  = "Face Not Recognized"
        binding.tvStatusSub.text   = "Please try again"
        binding.processingDots.visibility = View.GONE
    }

    private fun clearScan() {
        scanRunnable?.let { handler.removeCallbacks(it) }
        binding.scanLine.clearAnimation()
    }

    // ── PROCESSING DOTS ──────────────────────────────

    private fun animateProcessingDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { index, dot ->
            handler.postDelayed({
                dot.animate()
                    .alpha(1f).scaleX(1.2f).scaleY(1.2f)
                    .setDuration(400)
                    .withEndAction {
                        dot.animate()
                            .alpha(0.3f).scaleX(0.7f).scaleY(0.7f)
                            .setDuration(400).start()
                    }.start()
            }, (index * 200).toLong())
        }
    }

    // ── CLICK LISTENERS ──────────────────────────────

    private fun setupClickListeners() {
        binding.btnShutter.setOnClickListener { triggerScan() }
        binding.btnRetry.setOnClickListener   { resetToIdle() }
        binding.btnBack.setOnClickListener    {
            clearScan()
            requireActivity().supportFragmentManager.popBackStack()
        }
        binding.btnCancel.setOnClickListener  {
            clearScan()
            requireActivity().onBackPressed()
        }
    }
}