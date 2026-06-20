package com.facegate.storage

import com.facegate.storage.dao.AttendanceDao
import com.facegate.storage.dao.ClassAttendanceSummary
import com.facegate.storage.dao.ConflictDao
import com.facegate.storage.dao.StudentDao
import com.facegate.storage.dao.SyncLogDao
import com.facegate.storage.entity.AttendanceEntity
import com.facegate.storage.entity.ConflictEntity
import com.facegate.storage.entity.StudentEntity
import com.facegate.storage.entity.SyncLogEntity

class TemplateRepository(
    private val studentDao    : StudentDao,
    private val attendanceDao : AttendanceDao,
    private val syncLogDao    : SyncLogDao,
    private val conflictDao   : ConflictDao,
) {

    // ── Student ───────────────────────────────────────────────────────────────

    suspend fun addStudent(student: StudentEntity) =
        studentDao.insertStudent(student)

    suspend fun getStudents(): List<StudentEntity> =
        studentDao.getAllStudents()

    suspend fun getStudentsByClass(studentClass: String): List<StudentEntity> =
        studentDao.getStudentsByClass(studentClass)

    suspend fun getAllClasses(): List<String> =
        studentDao.getAllClasses()

    suspend fun getStudentCount(): Int =
        studentDao.getStudentCount()

    suspend fun deleteStudent(studentId: String) =
        studentDao.deleteStudent(studentId)

    // ── Attendance ────────────────────────────────────────────────────────────

    suspend fun addAttendance(record: AttendanceEntity) =
        attendanceDao.insertAttendance(record)

    suspend fun getUnsyncedAttendance(): List<AttendanceEntity> =
        attendanceDao.getUnsyncedRecords()

    suspend fun markAttendanceSynced(id: Int) =
        attendanceDao.markAsSynced(id)

    suspend fun getTodayAttendance(startOfDay: Long): List<AttendanceEntity> =
        attendanceDao.getTodayAttendance(startOfDay)

    suspend fun getAllAttendance(): List<AttendanceEntity> =
        attendanceDao.getAllAttendance()

    suspend fun getClassWiseAttendance(startOfDay: Long): List<ClassAttendanceSummary> =
        attendanceDao.getClassWiseAttendance(startOfDay)

    suspend fun isStudentMarkedToday(studentId: String, startOfDay: Long): Boolean =
        attendanceDao.isStudentMarkedToday(studentId, startOfDay) > 0

    // ── Sync Logs ─────────────────────────────────────────────────────────────

    suspend fun addSyncLog(log: SyncLogEntity) =
        syncLogDao.insertLog(log)

    suspend fun getSyncLogs(): List<SyncLogEntity> =
        syncLogDao.getAllLogs()

    // ── Conflict Queue ────────────────────────────────────────────────────────

    suspend fun addConflict(conflict: ConflictEntity) =
        conflictDao.insertConflict(conflict)

    suspend fun getUnresolvedConflicts(): List<ConflictEntity> =
        conflictDao.getUnresolvedConflicts()

    suspend fun getAllConflicts(): List<ConflictEntity> =
        conflictDao.getAllConflicts()

    suspend fun resolveConflict(id: Int) =
        conflictDao.markResolved(id)

    suspend fun getUnresolvedConflictCount(): Int =
        conflictDao.getUnresolvedCount()
}