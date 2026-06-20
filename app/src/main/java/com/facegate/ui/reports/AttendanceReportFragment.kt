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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    // ── Observe real DB stats ─────────────────────────────────────────────────

    private fun observeStats() {
        lifecycleScope.launch {
            viewModel.stats.collect { stats ->

                // Overall today's attendance percentage
                binding.tvMonthlyPct.text   = stats.attendancePct

                // Today's present / absent counts
                binding.tvPresentCount.text = stats.presentToday.toString()
                binding.tvAbsentCount.text  = stats.absentToday.toString()

                // Total records ever (historical)
                binding.tvTopClass.text     = "${stats.totalRecordsEver} total records"

                // Class-wise breakdown
                buildClassBreakdown(stats.classBreakdown, stats.totalStudents)
            }
        }
    }

    // ── Class-wise breakdown rows ─────────────────────────────────────────────

    private fun buildClassBreakdown(
        breakdown     : List<ClassAttendanceSummary>,
        totalStudents : Int,
    ) {
        // Only build if the container exists in the layout
        val container = binding.classBreakdownContainer ?: return
        container.removeAllViews()

        if (breakdown.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text      = "No attendance recorded today"
                textSize  = 13f
                gravity   = Gravity.CENTER
                setTextColor(Color.parseColor("#888780"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 24 }
            }
            container.addView(tv)
            return
        }

        breakdown.forEach { summary ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            val classLabel = TextView(requireContext()).apply {
                text     = "Class ${summary.studentClass}"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1A202C"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val countLabel = TextView(requireContext()).apply {
                text     = "${summary.presentCount} present"
                textSize = 13f
                setTextColor(Color.parseColor("#1D9E75"))
                gravity  = Gravity.END
            }

            row.addView(classLabel)
            row.addView(countLabel)
            container.addView(row)

            // Divider
            val div = View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#0F000000"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
            }
            container.addView(div)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnExportExcel.setOnClickListener {
            // TODO: export when backend ready
        }
        binding.btnExportPdf.setOnClickListener {
            // TODO: export when backend ready
        }
    }
}