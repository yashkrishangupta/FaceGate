package com.facegate.ui.admin

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facegate.R
import com.facegate.databinding.FragmentStudentBinding
import com.facegate.storage.entity.StudentEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentsFragment : Fragment() {

    private var _binding: FragmentStudentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudentsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeStudents()
    }

    override fun onResume() {
        super.onResume()
        // Refresh list when returning from enrollment
        viewModel.loadStudents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Observe DB ───────────────────────────────────────────────────────────

    private fun observeStudents() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.studentListContainer.removeAllViews()
                when (state) {
                    is StudentsState.Loading -> showMessage("Loading students…")
                    is StudentsState.Empty   -> showMessage("No students enrolled yet.\nTap + to enrol a student.")
                    is StudentsState.Loaded  -> {
                        updateStudentCount(state.students.size)
                        state.students.forEachIndexed { index, student ->
                            buildStudentRow(student, index, state.students.size)
                        }
                    }
                }
            }
        }
    }

    // ── Build student row ────────────────────────────────────────────────────

    private fun buildStudentRow(
        student : StudentEntity,
        index   : Int,
        total   : Int,
    ) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(40, 28, 40, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        // Avatar — initials
        val initials = student.name
            .split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")

        val avatar = TextView(requireContext()).apply {
            text      = initials
            textSize  = 13f
            typeface  = Typeface.DEFAULT_BOLD
            gravity   = Gravity.CENTER
            setTextColor(Color.parseColor("#1D9E75"))
            setBackgroundResource(R.drawable.chip_active)
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { marginEnd = 28 }
        }

        // Info column
        val infoCol = LinearLayout(requireContext()).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(requireContext()).apply {
            text     = student.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
        }

        val subText = TextView(requireContext()).apply {
            text     = "${student.studentId}  •  Class ${student.studentClass}"
            textSize = 11f
            setTextColor(Color.parseColor("#888780"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 4 }
        }

        infoCol.addView(nameText)
        infoCol.addView(subText)

        // Delete button
        val deleteBtn = TextView(requireContext()).apply {
            text     = "✕"
            textSize = 14f
            gravity  = Gravity.CENTER
            setTextColor(Color.parseColor("#D85A30"))
            layoutParams = LinearLayout.LayoutParams(64, 64)
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmDelete(student) }
        }

        row.addView(avatar)
        row.addView(infoCol)
        row.addView(deleteBtn)
        binding.studentListContainer.addView(row)

        // Divider except last
        if (index < total - 1) {
            val divider = View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#0F000000"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { marginStart = 40; marginEnd = 40 }
            }
            binding.studentListContainer.addView(divider)
        }
    }

    private fun confirmDelete(student: StudentEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove student?")
            .setMessage("Remove ${student.name} from the system? This cannot be undone.")
            .setPositiveButton("Remove") { _, _ -> viewModel.deleteStudent(student.studentId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStudentCount(count: Int) {
        binding.tvStudentCount.text = "$count students enrolled"
    }

    private fun showMessage(msg: String) {
        val tv = TextView(requireContext()).apply {
            text      = msg
            textSize  = 14f
            gravity   = Gravity.CENTER
            setTextColor(Color.parseColor("#888780"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 80 }
        }
        binding.studentListContainer.addView(tv)
    }

    // ── Click listeners ──────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnEnrollNew.setOnClickListener {
            findNavController().navigate(R.id.action_students_to_enrollment)
        }
    }
}