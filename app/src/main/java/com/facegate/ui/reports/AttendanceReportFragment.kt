package com.facegate.ui.reports

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facegate.databinding.FragmentAttendanceReportBinding
import com.facegate.storage.dao.ClassAttendanceSummary
import com.facegate.storage.entity.SessionEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AttendanceReportFragment : Fragment() {

    private var _binding: FragmentAttendanceReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAttendanceReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeStats()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Observe stats ─────────────────────────────────────────────────────────

    private fun observeStats() {
        lifecycleScope.launch {
            viewModel.stats.collect { stats ->

                // ── Holiday state ──────────────────────────────────────────
                if (stats.isHoliday) {
                    binding.tvMonthlyPct.text   = "Holiday"
                    binding.tvPresentCount.text = "-"
                    binding.tvAbsentCount.text  = "-"
                    binding.tvTopClass.text     = "Holiday — ${stats.holidayName}"
                    binding.classBreakdownContainer?.removeAllViews()
                    //binding.sessionBreakdownContainer?.removeAllViews()
                    return@collect
                }

                // ── Normal state ───────────────────────────────────────────
                binding.tvMonthlyPct.text   = stats.attendancePct
                binding.tvPresentCount.text = stats.presentToday.toString()
                binding.tvAbsentCount.text  = stats.absentToday.toString()
                binding.tvTopClass.text     = "${stats.totalRecordsEver} total records"

                buildClassBreakdown(stats.classBreakdown, stats.totalStudents)
                buildSessionBreakdown(stats.sessionBreakdown)
            }
        }
    }

    // ── Class-wise breakdown ──────────────────────────────────────────────────

    private fun buildClassBreakdown(
        breakdown     : List<ClassAttendanceSummary>,
        totalStudents : Int,
    ) {
        val container = binding.classBreakdownContainer ?: return
        container.removeAllViews()

        if (breakdown.isEmpty()) {
            container.addView(emptyTextView("No attendance recorded today"))
            return
        }

        breakdown.forEach { summary ->
            val row = rowLayout()
            row.addView(labelText("Class ${summary.studentClass}", bold = true, flex = 1f))
            row.addView(labelText("${summary.presentCount} present", color = "#1D9E75"))
            container.addView(row)
            container.addView(divider())
        }
    }

    // ── Session breakdown ─────────────────────────────────────────────────────

    private fun buildSessionBreakdown(
        sessions: List<Pair<SessionEntity, Int>>,
    ) {
        // sessionBreakdownContainer not in layout yet — skip
    }
    // ── View helpers ──────────────────────────────────────────────────────────

    private fun rowLayout() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setPadding(0, 12, 0, 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun labelText(
        text  : String,
        bold  : Boolean = false,
        flex  : Float   = 0f,
        color : String  = "#1A202C",
    ) = TextView(requireContext()).apply {
        this.text     = text
        textSize      = 13f
        typeface      = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(Color.parseColor(color))
        layoutParams  = if (flex > 0f)
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, flex)
        else
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.END }
    }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(Color.parseColor("#0F000000"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun emptyTextView(msg: String) = TextView(requireContext()).apply {
        text      = msg
        textSize  = 13f
        gravity   = Gravity.CENTER
        setTextColor(Color.parseColor("#888780"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = 24 }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnExportExcel.setOnClickListener { /* TODO */ }
        binding.btnExportPdf.setOnClickListener { /* TODO */ }
    }
}
