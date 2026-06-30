package com.facegate.ui.admin

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facegate.storage.entity.OverrideEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ChangesLogFragment : Fragment() {

    private val viewModel: ChangesLogViewModel by viewModels()
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
            text     = "  Changes Log"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
        }
        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

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

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeOverrides()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll()
    }

    // ── Observe overrides ──────────────────────────────────────────────────────

    private fun observeOverrides() {
        lifecycleScope.launch {
            viewModel.overrides.collect { overrides ->
                buildList(overrides)
            }
        }
    }

    // ── Build list ─────────────────────────────────────────────────────────────

    private fun buildList(overrides: List<OverrideEntity>) {
        listContainer.removeAllViews()

        if (overrides.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text     = "No changes recorded yet"
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

        overrides.forEach { override ->
            val card = buildCard(override)
            listContainer.addView(card)
        }
    }

    // ── Card per override ──────────────────────────────────────────────────────

    private fun buildCard(override: OverrideEntity): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 12 }
        }

        // ── Top row: field chip + session info ─────────────────────────────
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val chip = TextView(requireContext()).apply {
            text      = override.fieldChanged.uppercase()
            textSize  = 11f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setPadding(16, 6, 16, 6)
        }
        val tvSession = TextView(requireContext()).apply {
            text     = "  Session: ${override.sessionId.take(8)}..."
            textSize = 12f
            setTextColor(Color.parseColor("#888780"))
        }
        topRow.addView(chip)
        topRow.addView(tvSession)
        card.addView(topRow)

        // ── Middle: old → new ──────────────────────────────────────────────
        val tvChange = TextView(requireContext()).apply {
            text     = "${override.oldValue}  →  ${override.newValue}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A202C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 10 }
        }
        card.addView(tvChange)

        // ── Bottom: timestamp + reason ─────────────────────────────────────
        val dateStr = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            .format(Date(override.changedAt))
        val reasonStr = if (override.reason.isNotEmpty()) "  •  ${override.reason}" else ""

        val tvMeta = TextView(requireContext()).apply {
            text     = "$dateStr$reasonStr"
            textSize = 12f
            setTextColor(Color.parseColor("#888780"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 6 }
        }
        card.addView(tvMeta)

        return card
    }
}
