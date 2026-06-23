package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecognitionRecordFormatter(
    private val emptyText: String = "暂无记录",
    private val locale: Locale = Locale.CHINA
) {

    private val timeFormatter = SimpleDateFormat("MM-dd HH:mm:ss", locale)

    fun format(records: List<RecognitionRecord>): String {
        if (records.isEmpty()) return emptyText

        val visibleRecords = records.take(MAX_VISIBLE_RECORDS)
        val hiddenCount = records.size - visibleRecords.size
        val visibleText = visibleRecords.joinToString(separator = "\n\n") { record ->
            if (record.status == RecognitionStatus.LIVE_DEMO) {
                "${timeFormatter.format(Date(record.timestamp))}  ${record.status}\n" +
                    "结果：${record.explanation.lineSequence().firstOrNull().orEmpty()}"
            } else {
                "${timeFormatter.format(Date(record.timestamp))}  ${record.status}\n" +
                    "姓名：${record.name}  相似度：${formatPercent(record.confidence)}  距离：${formatDistance(record.distance)}"
            }
        }
        return if (hiddenCount > 0) {
            "$visibleText\n\n仅显示最近 $MAX_VISIBLE_RECORDS 条，另有 $hiddenCount 条可通过“复制完整报告”带入报告。"
        } else {
            visibleText
        }
    }

    fun formatFull(records: List<RecognitionRecord>): String {
        if (records.isEmpty()) return emptyText

        return records.joinToString(separator = "\n\n") { record ->
            if (record.status == RecognitionStatus.LIVE_DEMO) {
                "${timeFormatter.format(Date(record.timestamp))}  ${record.status}\n" +
                    "类型：演示记录\n" +
                    "说明：${record.explanation}"
            } else {
                "${timeFormatter.format(Date(record.timestamp))}  ${record.status}\n" +
                    "姓名：${record.name}\n" +
                    "相似度：${formatPercent(record.confidence)}\n" +
                    "距离：${formatDistance(record.distance)}\n" +
                    "说明：${record.explanation.ifBlank { "无补充说明" }}"
            }
        }
    }

    private fun formatPercent(value: Float): String {
        return String.format(locale, "%.1f%%", value)
    }

    private fun formatDistance(value: Float): String {
        return if (value == Float.MAX_VALUE) "--" else String.format(locale, "%.3f", value)
    }

    companion object {
        private const val MAX_VISIBLE_RECORDS = 3
    }
}
