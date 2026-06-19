package com.facegate.ui.admin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.facegate.databinding.FragmentAdminDashboardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

/**
 * ADMIN DASHBOARD FRAGMENT
 * Matches: #s-dashboard in HTML
 * Shows stats, quick actions, bottom nav
 */
@AndroidEntryPoint
class AdminDashboard : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    // Live clock handler
    // Matches: setInterval(() => {...}, 1000) in JS
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    // ── LIFECYCLE ────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(
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
        updateDate()
        clockHandler.post(clockRunnable)
        loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clockHandler.removeCallbacks(clockRunnable)
        _binding = null
    }

    // ── CLOCK & DATE ─────────────────────────────────

    private fun updateClock() {
        val time = SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date())
        binding.tvClock.text = time
    }

    private fun updateDate() {
        val date = SimpleDateFormat(
            "EEEE, d MMMM yyyy",
            Locale.getDefault()
        ).format(Date())
        binding.tvDate.text = date
    }

    // ── STATS ────────────────────────────────────────

    private fun loadStats() {
        binding.tvTotalStudents.text = "248"
        binding.tvPresentToday.text  = "211"
        binding.tvAbsentToday.text   = "37"
        binding.tvHolidaysLeft.text  = "8"
    }

    // ── CLICK LISTENERS ──────────────────────────────

    private fun setupClickListeners() {

        // Quick action tiles
        binding.tileStudents.setOnClickListener {
            showToast("Manage Students — coming soon")
        }

        binding.tileManual.setOnClickListener {
            showToast("Manual Attendance — coming soon")
        }

        binding.tileHolidays.setOnClickListener {
            showToast("Holidays — coming soon")
        }

        binding.tileReports.setOnClickListener {
            showToast("Reports — coming soon")
        }

        // Conflict banner
        binding.btnResolve.setOnClickListener {
            showToast("Conflict Queue — coming soon")
        }

        // Bottom navigation
        binding.navStudents.setOnClickListener {
            showToast("Students — coming soon")
        }

        binding.navAttendance.setOnClickListener {
            showToast("Attendance — coming soon")
        }

        binding.navReports.setOnClickListener {
            showToast("Reports — coming soon")
        }

        binding.navExit.setOnClickListener {
            // Go back to welcome
            requireActivity().finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}