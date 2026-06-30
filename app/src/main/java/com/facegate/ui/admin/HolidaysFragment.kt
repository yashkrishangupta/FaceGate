package com.facegate.ui.admin

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import com.facegate.storage.entity.HolidayEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HolidaysFragment : Fragment() {

    private val viewModel: HolidaySetupViewModel by viewModels()
    private lateinit var listContainer: LinearLayout

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
            text     = "←"
            textSize = 22f
            setTextColor(Color.parseColor("#1D9E75"))
            setOnClickListener { findNavController().navigateUp() }
        }
        val tvTitle = TextView(requireContext()).apply {
            text     = "  Holidays"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
        }
        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        val tvSub = TextView(requireContext()).apply {
            text     = "Attendance is not recorded on holiday dates."
            textSize = 13f
            setTextColor(Color.parseColor("#888780"))
            setPadding(32, 0, 32, 16)
        }
        root.addView(tvSub)

        // ── List ───────────────────────────────────────────────────────────
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 24)
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        // ── Add Holiday button ─────────────────────────────────────────────
        val btnAdd = TextView(requireContext()).apply {
            text     = "+ Add Holiday"
            textSize = 15f
            gravity  = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D9E75"))
            setPadding(0, 32, 0, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { showAddHolidayDialog() }
        }
        root.addView(btnAdd)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeHolidays()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll()
    }

    // ── Observe holidays ───────────────────────────────────────────────────────

    private fun observeHolidays() {
        lifecycleScope.launch {
            viewModel.holidays.collect { holidays ->
                buildList(holidays)
            }
        }
    }

    // ── Build list ─────────────────────────────────────────────────────────────

    private fun buildList(holidays: List<HolidayEntity>) {
        listContainer.removeAllViews()

        if (holidays.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text     = "No holidays added yet"
                textSize = 14f
                gravity  = Gravity.CENTER
                setTextColor(Color.parseColor("#888780"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 64 }
            }
            listContainer.addView(empty)
            return
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        holidays.forEach { holiday ->
            val isPast = holiday.date < today
            val card   = buildHolidayCard(holiday, isPast)
            listContainer.addView(card)
        }
    }

    // ── Holiday card ───────────────────────────────────────────────────────────

    private fun buildHolidayCard(holiday: HolidayEntity, isPast: Boolean): LinearLayout {
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

        val textColor = if (isPast) "#AAAAAA" else "#1A202C"

        val info = LinearLayout(requireContext()).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvName = TextView(requireContext()).apply {
            text     = holiday.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
        }
        val tvDate = TextView(requireContext()).apply {
            text     = holiday.date
            textSize = 12f
            setTextColor(Color.parseColor(if (isPast) "#CCCCCC" else "#888780"))
        }
        info.addView(tvName)
        info.addView(tvDate)

        val btnDelete = TextView(requireContext()).apply {
            text     = "Delete"
            textSize = 12f
            setTextColor(Color.parseColor("#E53935"))
            setPadding(16, 8, 8, 8)
            setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Holiday")
                    .setMessage("Remove ${holiday.name}?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteHoliday(holiday.date) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        card.addView(info)
        card.addView(btnDelete)
        return card
    }

    // ── Add holiday dialog ─────────────────────────────────────────────────────

    private fun showAddHolidayDialog() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                showNameDialog(dateStr)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun showNameDialog(dateStr: String) {
        val etName = EditText(requireContext()).apply {
            hint = "Holiday name"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Holiday name for $dateStr")
            .setView(etName)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addHoliday(HolidayEntity(
                        date      = dateStr,
                        name      = name,
                        createdAt = System.currentTimeMillis(),
                    ))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
