package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SessionHistoryRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("safewalk_sessions", Context.MODE_PRIVATE)

    private val _sessionsFlow = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessionsFlow: StateFlow<List<SessionRecord>> = _sessionsFlow.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        val jsonString = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        val list = mutableListOf<SessionRecord>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SessionRecord(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        startTime = obj.optLong("startTime", 0L),
                        endTime = obj.optLong("endTime", 0L),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        maxRiskScore = obj.optInt("maxRiskScore", 0),
                        maxRiskLevel = try {
                            RiskLevel.valueOf(obj.optString("maxRiskLevel", "SAFE"))
                        } catch (_: Exception) {
                            RiskLevel.SAFE
                        },
                        unusualEventsCount = obj.optInt("unusualEventsCount", 0),
                        cancellationCount = obj.optInt("cancellationCount", 0),
                        alertTriggered = obj.optBoolean("alertTriggered", false),
                        finalLatitude = if (obj.has("finalLat")) obj.getDouble("finalLat") else null,
                        finalLongitude = if (obj.has("finalLng")) obj.getDouble("finalLng") else null
                    )
                )
            }
        } catch (_: Exception) {
            // fallback gracefully
        }
        _sessionsFlow.value = list.sortedByDescending { it.startTime }
    }

    fun saveSession(session: SessionRecord) {
        val current = _sessionsFlow.value.toMutableList()
        current.add(0, session)
        persist(current)
    }

    fun clearHistory() {
        persist(emptyList())
    }

    private fun persist(list: List<SessionRecord>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("startTime", item.startTime)
            obj.put("endTime", item.endTime)
            obj.put("durationSeconds", item.durationSeconds)
            obj.put("maxRiskScore", item.maxRiskScore)
            obj.put("maxRiskLevel", item.maxRiskLevel.name)
            obj.put("unusualEventsCount", item.unusualEventsCount)
            obj.put("cancellationCount", item.cancellationCount)
            obj.put("alertTriggered", item.alertTriggered)
            item.finalLatitude?.let { obj.put("finalLat", it) }
            item.finalLongitude?.let { obj.put("finalLng", it) }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
        _sessionsFlow.value = list.sortedByDescending { it.startTime }
    }

    companion object {
        private const val KEY_SESSIONS = "all_sessions"
    }
}
