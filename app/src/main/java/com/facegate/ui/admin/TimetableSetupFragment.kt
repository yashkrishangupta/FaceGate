package com.facegate.ui.admin

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facegate.storage.entity.TimetableEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimetableSetupFragment : Fragment() {

    private val viewModel: TimetableSetupViewModel by viewModels()
    private var selectedDay = 1  // default Monday

    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    private lateinit var periodListContainer: LinearLayout
    private lateinit var emptyState: TextView
    private val dayTabButtons = mutableListOf<TextView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        // ── Top bar ────────────────────────────────────────────────────────
        val topBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(32, 60, 32, 16)
        }
        val btnBack = TextView(requireContext()).apply {
            text      = "←"
            textSize  = 22f
            setTextColor(Color.parseColor("#1D9E75"))
            setOnClickListener { findNavController().navigateUp() }
        }
        val tvTitle = TextView(requireContext()).apply {
            text     = "  Timetable Setup"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
        }
        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        // ── Day tabs ───────────────────────────────────────────────────────
        val tabScroll = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val tabRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 8, 24, 8)
        }
        dayNames.forEachIndexed { index, name ->
            val tab = TextView(requireContext()).apply {
                text    = name
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#1A202C"))
                setBackgroundColor(if (index == 0) Color.parseColor("#1D9E75") else Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 8 }
                setOnClickListener { onDaySelected(index + 1) }
            }
            dayTabButtons.add(tab)
            tabRow.addView(tab)
        }
        tabScroll.addView(tabRow)
        root.addView(tabScroll)

        // ── Period list ────────────────────────────────────────────────────
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        periodListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        emptyState = TextView(requireContext()).apply {
            text      = "No periods for this day — tap + to add"
            textSize  = 14f
            gravity   = Gravity.CENTER
            setTextColor(Color.parseColor("#888780"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 48 }
        }
        periodListContainer.addView(emptyState)
        scroll.addView(periodListContainer)
        root.addView(scroll)

        // ── Add Period button ──────────────────────────────────────────────
        val btnAdd = TextView(requireContext()).apply {
            text      = "+ Add Period"
            textSize  = 15f
            gravity   = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D9E75"))
            setPadding(0, 32, 0, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { showAddPeriodDialog() }
        }
        root.addView(btnAdd)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadSubjectsAndBatches()
        observeEntries()
    }

    // ── Observe entries ────────────────────────────────────────────────────────

    private fun observeEntries() {
        lifecycleScope.launch {
            viewModel.allEntries.collect {
                refreshPeriodList()
            }
        }
    }

    // ── Day tab selection ──────────────────────────────────────────────────────

    private fun onDaySelected(day: Int) {
        selectedDay = day
        dayTabButtons.forEachIndexed { i, tab ->
            if (i + 1 == day) {
                tab.setTextColor(Color.WHITE)
                tab.setBackgroundColor(Color.parseColor("#1D9E75"))
            } else {
                tab.setTextColor(Color.parseColor("#1A202C"))
                tab.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        refreshPeriodList()
    }

    // ── Refresh period list ────────────────────────────────────────────────────

    private fun refreshPeriodList() {
        val entries = viewModel.getForDay(selectedDay)
        // Remove all views except emptyState
        periodListContainer.removeAllViews()
        periodListContainer.addView(emptyState)

        if (entries.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE

        entries.forEach { entry ->
            val card = buildPeriodCard(entry)
            periodListContainer.addView(card)
        }
    }

    // ── Period card ────────────────────────────────────────────────────────────

    private fun buildPeriodCard(entry: TimetableEntity): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 8 }
        }

        val chip = TextView(requireContext()).apply {
            text      = "P${entry.periodNumber}"
            textSize  = 12f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D9E75"))
            setPadding(16, 8, 16, 8)
            gravity   = Gravity.CENTER
        }

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 16
            }
        }
        val tvSubject = TextView(requireContext()).apply {
            text     = entry.subject
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
        }
        val tvBatch = TextView(requireContext()).apply {
            text     = "${entry.batch}  •  ${entry.scheduledHour}:${entry.scheduledMinute.toString().padStart(2,'0')}  •  ${entry.windowMinutes}min"
            textSize = 12f
            setTextColor(Color.parseColor("#888780"))
        }
        info.addView(tvSubject)
        info.addView(tvBatch)

        val btnEdit = TextView(requireContext()).apply {
            text     = "Edit"
            textSize = 12f
            setTextColor(Color.parseColor("#1D9E75"))
            setPadding(16, 8, 8, 8)
            setOnClickListener { showAddPeriodDialog(entry) }
        }
        val btnDelete = TextView(requireContext()).apply {
            text     = "Delete"
            textSize = 12f
            setTextColor(Color.parseColor("#E53935"))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Period")
                    .setMessage("Delete P${entry.periodNumber} — ${entry.subject}?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePeriod(entry.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        card.addView(chip)
        card.addView(info)
        card.addView(btnEdit)
        card.addView(btnDelete)
        return card
    }

    // ── Add / Edit dialog ──────────────────────────────────────────────────────

    private fun showAddPeriodDialog(existing: TimetableEntity? = null) {
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val spinnerPeriod = Spinner(requireContext())
        val periods       = (1..8).map { "Period $it" }
        spinnerPeriod.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, periods)
        existing?.let { spinnerPeriod.setSelection(it.periodNumber - 1) }

        val etSubject = EditText(requireContext()).apply {
            hint = "Subject"
            setText(existing?.subject ?: "")
        }
        val etBatch = EditText(requireContext()).apply {
            hint = "Batch (e.g. 9-A)"
            setText(existing?.batch ?: "")
        }

        var selectedHour   = existing?.scheduledHour   ?: 8
        var selectedMinute = existing?.scheduledMinute ?: 0

        val tvTime = TextView(requireContext()).apply {
            text     = "Time: ${selectedHour}:${selectedMinute.toString().padStart(2, '0')}"
            textSize = 14f
            setTextColor(Color.parseColor("#1D9E75"))
            setPadding(0, 16, 0, 8)
            setOnClickListener {
                TimePickerDialog(requireContext(), { _, h, m ->
                    selectedHour   = h
                    selectedMinute = m
                    text = "Time: ${h}:${m.toString().padStart(2, '0')}"
                }, selectedHour, selectedMinute, true).show()
            }
        }

        val etWindow = EditText(requireContext()).apply {
            hint      = "Window minutes (default 10)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existing?.windowMinutes?.toString() ?: "10")
        }

        dialogLayout.addView(TextView(requireContext()).apply { text = "Period"; textSize = 13f })
        dialogLayout.addView(spinnerPeriod)
        dialogLayout.addView(etSubject)
        dialogLayout.addView(etBatch)
        dialogLayout.addView(tvTime)
        dialogLayout.addView(etWindow)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Add Period" else "Edit Period")
            .setView(dialogLayout)
            .setPositiveButton("Save") { _, _ ->
                val entry = TimetableEntity(
                    id              = existing?.id ?: 0,
                    dayOfWeek       = selectedDay,
                    periodNumber    = spinnerPeriod.selectedItemPosition + 1,
                    subject         = etSubject.text.toString().trim(),
                    batch           = etBatch.text.toString().trim(),
                    scheduledHour   = selectedHour,
                    scheduledMinute = selectedMinute,
                    windowMinutes   = etWindow.text.toString().toIntOrNull() ?: 10,
                )
                viewModel.addOrUpdatePeriod(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
