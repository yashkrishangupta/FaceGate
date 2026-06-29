package com.facegate.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.storage.TemplateRepository
import com.facegate.storage.dao.ClassAttendanceSummary
import com.facegate.storage.entity.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ReportStats(
    val totalStudents     : Int                          = 0,
    val presentToday      : Int                          = 0,
    val absentToday       : Int                          = 0,
    val attendancePct     : String                       = "0%",
    val totalRecordsEver  : Int                          = 0,
    val classBreakdown    : List<ClassAttendanceSummary> = emptyList(),
    val sessionBreakdown  : List<Pair<SessionEntity, Int>> = emptyList(),
    val isHoliday         : Boolean                      = false,
    val holidayName       : String                       = "",
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: TemplateRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(ReportStats())
    val stats: StateFlow<ReportStats> = _stats

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            val today      = getTodayString()
            val startOfDay = getStartOfDay()
            val endOfDay   = getEndOfDay()

            // ── Holiday check ──────────────────────────────────────────────
            val holiday = repository.isHoliday(today)
            if (holiday) {
                val holidays    = repository.getAllHolidays()
                val holidayName = holidays.firstOrNull { it.date == today }?.name ?: "Holiday"
                _stats.value = ReportStats(isHoliday = true, holidayName = holidayName)
                return@launch
            }

            // ── Attendance stats ───────────────────────────────────────────
            val totalStudents    = repository.getStudentCount()
            val presentToday     = repository.getTodayAttendance(startOfDay).size
            val absentToday      = (totalStudents - presentToday).coerceAtLeast(0)
            val totalRecordsEver = repository.getAllAttendance().size
            val classBreakdown   = repository.getClassWiseAttendance(startOfDay)

            val pct = if (totalStudents > 0)
                "${((presentToday.toFloat() / totalStudents) * 100).toInt()}%"
            else "0%"

            // ── Session breakdown ──────────────────────────────────────────
            val sessionBreakdown = loadSessionBreakdown(startOfDay, endOfDay)

            _stats.value = ReportStats(
                totalStudents    = totalStudents,
                presentToday     = presentToday,
                absentToday      = absentToday,
                attendancePct    = pct,
                totalRecordsEver = totalRecordsEver,
                classBreakdown   = classBreakdown,
                sessionBreakdown = sessionBreakdown,
                isHoliday        = false,
            )
        }
    }

    // ── Session breakdown — sessions for today + present count per session ──
    private suspend fun loadSessionBreakdown(
        startOfDay: Long,
        endOfDay: Long,
    ): List<Pair<SessionEntity, Int>> {
        val sessions   = repository.getSessionsForDate(startOfDay, endOfDay)
        val attendance = repository.getTodayAttendance(startOfDay)

        return sessions.map { session ->
            val count = attendance.count { it.sessionId == session.sessionId }
            Pair(session, count)
        }
    }

    // ── Date helpers ───────────────────────────────────────────────────────

    private fun getTodayString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getEndOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
