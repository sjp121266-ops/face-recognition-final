package com.example.facerecognitionfinal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FaceStore(context: Context) {

    private val profilesFile = File(context.filesDir, "profiles.json")
    private val recordsFile = File(context.filesDir, "records.json")

    var lastWarning: String? = null
        private set

    init {
        migrateFromSharedPreferences(context)
    }

    fun loadProfiles(): MutableList<PersonProfile> {
        if (!profilesFile.exists()) return mutableListOf()
        val raw = try {
            profilesFile.readText()
        } catch (_: Exception) {
            return mutableListOf()
        }
        return try {
            val result = parseProfilesLenient(JSONArray(raw))
            if (result.skippedItems > 0) {
                lastWarning = "检测到本地人脸库中有 ${result.skippedItems} 项异常数据，已保留可用人员并自动修复。"
                saveProfiles(result.profiles)
            }
            result.profiles
        } catch (_: Exception) {
            profilesFile.delete()
            lastWarning = "检测到本地人脸库数据异常，已自动清空，请重新录入人员。"
            mutableListOf()
        }
    }

    fun saveProfiles(profiles: List<PersonProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val embeddingsJson = JSONArray()
            profile.embeddings.forEach { embedding ->
                val values = JSONArray()
                embedding.forEach { values.put(it.toDouble()) }
                embeddingsJson.put(values)
            }
            array.put(
                JSONObject()
                    .put("name", profile.name)
                    .put("group", profile.group)
                    .put("embeddings", embeddingsJson)
            )
        }
        atomicWrite(profilesFile, array.toString())
    }

    private fun parseProfilesLenient(array: JSONArray): ProfileParseResult {
        val profiles = mutableListOf<PersonProfile>()
        var skippedItems = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item == null) {
                skippedItems++
                continue
            }
            val name = item.optString("name").trim()
            val group = item.optString("group", "默认").trim().ifBlank { "默认" }
            val embeddingsJson = item.optJSONArray("embeddings")
            if (name.isBlank() || embeddingsJson == null) {
                skippedItems++
                continue
            }
            val embeddings = mutableListOf<FloatArray>()
            for (j in 0 until embeddingsJson.length()) {
                val embedding = runCatching {
                    embeddingsJson.getJSONArray(j).toFloatArray()
                }.getOrNull()
                if (embedding == null) {
                    skippedItems++
                } else {
                    embeddings.add(embedding)
                }
            }
            if (embeddings.isEmpty()) {
                skippedItems++
            } else {
                profiles.add(PersonProfile(name, embeddings, group))
            }
        }
        return ProfileParseResult(profiles, skippedItems)
    }

    private data class ProfileParseResult(
        val profiles: MutableList<PersonProfile>,
        val skippedItems: Int
    )

    fun loadRecords(): MutableList<RecognitionRecord> {
        if (!recordsFile.exists()) return mutableListOf()
        val raw = try {
            recordsFile.readText()
        } catch (_: Exception) {
            return mutableListOf()
        }
        return try {
            val records = mutableListOf<RecognitionRecord>()
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                records.add(
                    RecognitionRecord(
                        timestamp = item.getLong("timestamp"),
                        name = item.getString("name"),
                        distance = item.getDouble("distance").toFloat(),
                        confidence = item.getDouble("confidence").toFloat(),
                        status = item.getString("status"),
                        explanation = item.optString("explanation", "")
                    )
                )
            }
            records
        } catch (_: Exception) {
            recordsFile.delete()
            lastWarning = "检测到本地识别记录异常，已自动清空记录。"
            mutableListOf()
        }
    }

    fun saveRecords(records: List<RecognitionRecord>) {
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put("timestamp", record.timestamp)
                    .put("name", record.name)
                    .put("distance", record.distance.toDouble())
                    .put("confidence", record.confidence.toDouble())
                    .put("status", record.status)
                    .put("explanation", record.explanation)
            )
        }
        atomicWrite(recordsFile, array.toString())
    }

    fun clearProfiles() {
        profilesFile.delete()
    }

    fun deletePerson(name: String) {
        val profiles = loadProfiles()
        profiles.removeAll { it.name == name }
        saveProfiles(profiles)
    }

    fun clearRecords() {
        recordsFile.delete()
    }

    private fun migrateFromSharedPreferences(context: Context) {
        if (profilesFile.exists() && recordsFile.exists()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefsProfiles = prefs.getString(KEY_PROFILES, null)
        val prefsRecords = prefs.getString(KEY_RECORDS, null)
        if (prefsProfiles != null && !profilesFile.exists()) {
            try {
                atomicWrite(profilesFile, prefsProfiles)
            } catch (_: Exception) { }
        }
        if (prefsRecords != null && !recordsFile.exists()) {
            try {
                atomicWrite(recordsFile, prefsRecords)
            } catch (_: Exception) { }
        }
        prefs.edit().clear().apply()
    }

    private fun JSONArray.toFloatArray(): FloatArray {
        return FloatArray(length()) { index -> getDouble(index).toFloat() }
    }

    private fun atomicWrite(file: File, content: String) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(content)
        if (!tempFile.renameTo(file)) {
            file.writeText(content)
            tempFile.delete()
        }
    }

    // ── Attendance storage ──
    private val attendanceFile = File(context.filesDir, "attendance.json")

    fun loadAttendanceRecords(): List<AttendanceRecord> {
        if (!attendanceFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(attendanceFile.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AttendanceRecord(
                    name = obj.getString("name"),
                    timestamp = obj.getString("timestamp"),
                    date = obj.getString("date"),
                    confidence = obj.getDouble("confidence").toFloat(),
                    method = obj.optString("method", "人脸识别")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveAttendanceRecords(records: List<AttendanceRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("name", r.name)
                put("timestamp", r.timestamp)
                put("date", r.date)
                put("confidence", r.confidence.toDouble())
                put("method", r.method)
            })
        }
        atomicWrite(attendanceFile, arr.toString())
    }

    fun clearAttendanceRecords() {
        attendanceFile.delete()
    }

    companion object {
        private const val PREFS_NAME = "face_store"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 30
    }
}
