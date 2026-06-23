package com.example.facerecognitionfinal.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages attendance check-in records in memory.
 * Persists via JSON to app-private storage through FaceStore.
 */
class AttendanceManager(
    private val loadRecords: () -> List<AttendanceRecord>,
    private val saveRecords: (List<AttendanceRecord>) -> Unit
) {
    private val _records: MutableList<AttendanceRecord> = loadRecords().toMutableList()
    val records: List<AttendanceRecord> get() = _records

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun checkIn(name: String, confidence: Float) {
        val now = Date()
        val record = AttendanceRecord(
            name = name,
            timestamp = dateFormat.format(now),
            date = dayFormat.format(now),
            confidence = confidence
        )
        // Avoid duplicate check-ins within 30 seconds for same person
        val recentDuplicate = _records.any {
            it.name == name && it.date == record.date &&
                    (now.time - dateFormat.parse(it.timestamp)!!.time) < 30_000
        }
        if (!recentDuplicate) {
            _records.add(0, record)
            // Keep last 500 records
            if (_records.size > 500) {
                _records.removeAt(_records.size - 1)
            }
            saveRecords(_records)
        }
    }

    fun getTodayAttendees(): List<AttendanceRecord> {
        val today = dayFormat.format(Date())
        return _records.filter { it.date == today }
    }

    fun getAttendeesByDate(date: String): List<AttendanceRecord> {
        return _records.filter { it.date == date }
    }

    fun getUniqueAttendeeNames(): List<String> {
        return _records.map { it.name }.distinct()
    }

    fun getCheckInCountByPerson(): Map<String, Int> {
        return _records.groupBy { it.name }.mapValues { it.value.size }
    }

    fun clear() {
        _records.clear()
        saveRecords(_records)
    }

    fun exportCsv(): String {
        val sb = StringBuilder("姓名,时间,置信度,方式\n")
        _records.forEach { r ->
            sb.append("${r.name},${r.timestamp},${"%.1f".format(r.confidence)}%,${r.method}\n")
        }
        return sb.toString()
    }

    fun exportTodaySummary(): String {
        val today = getTodayAttendees()
        if (today.isEmpty()) return "今日暂无考勤记录。"
        val names = today.map { it.name }.distinct().sorted()
        val sb = StringBuilder("📋 今日考勤 (${dayFormat.format(Date())})\n")
        sb.append("应到/实到: ${names.size} 人\n")
        sb.append("━".repeat(30) + "\n")
        names.forEach { name ->
            val latest = today.last { it.name == name }
            sb.append("✅ $name  ${latest.timestamp.split(" ")[1]}\n")
        }
        return sb.toString()
    }
}
