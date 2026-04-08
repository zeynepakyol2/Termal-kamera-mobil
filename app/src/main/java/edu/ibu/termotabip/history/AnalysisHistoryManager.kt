package edu.ibu.termotabip.history

import android.content.Context
import android.util.Log
import edu.ibu.termotabip.model.WoundAnalysisResult
import edu.ibu.termotabip.model.WoundPresenceResult
import edu.ibu.termotabip.model.WoundStageResult
import edu.ibu.termotabip.model.WoundRiskResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG       = "AnalysisHistoryManager"
private const val FILE_NAME = "analysis_history.json"
private const val MAX_ITEMS = 100

data class AnalysisRecord(
    val id          : String,
    val date        : String,           // "17 Mart 2026 14:35"
    val timestamp   : Long,
    val username    : String,
    val imageUri    : String,
    val hasWound    : Boolean,
    val presenceConf: Float,
    // Yara varsa
    val stage       : Int?   = null,
    val stageConf   : Float? = null,
    val stageName   : String? = null,
    var woundArea: Double? = null,
    var woundWidth: Double? = null,
    var woundHeight: Double? = null,
    // Yara yoksa
    val riskLevel   : Int?   = null,
    val riskConf    : Float? = null,
    val riskName    : String? = null

) {
    /** Kısa özet metni */
    val summary: String get() = when {
        hasWound && stage != null -> stageName ?: "Evre ${stage + 1}"
        !hasWound && riskLevel != null -> riskName ?: "Risk ${riskLevel}"
        else -> "Analiz"
    }
    val confidencePct: Int get() = when {
        hasWound && stageConf != null -> (stageConf * 100).toInt()
        !hasWound && riskConf != null -> (riskConf * 100).toInt()
        else -> (presenceConf * 100).toInt()
    }
}

object AnalysisHistoryManager {

    // ── Kaydet ───────────────────────────────────────────────────────────
    fun save(
        context   : Context,
        result    : WoundAnalysisResult,
        imageUri  : String,
        username  : String
    ): AnalysisRecord {
        val formatter = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr"))
        formatter.timeZone = java.util.TimeZone.getTimeZone("Europe/Istanbul") //+3 ekledik
        val date = formatter.format(Date())
        val record = AnalysisRecord(
            id           = UUID.randomUUID().toString(),
            date         = date,
            timestamp    = System.currentTimeMillis(),
            username     = username,
            imageUri     = imageUri,
            hasWound     = result.presenceResult?.hasWound ?: false,
            presenceConf = result.presenceResult?.confidence ?: 0f,
            stage        = result.stageResult?.stage,
            stageConf    = result.stageResult?.confidence,
            stageName    = result.stageResult?.stageName,
            riskLevel    = result.riskResult?.riskLevel,
            riskConf     = result.riskResult?.confidence,
            riskName     = result.riskResult?.riskName
        )

        val list = loadAll(context).toMutableList()
        list.add(0, record)  // en yeni başa
        if (list.size > MAX_ITEMS) list.subList(MAX_ITEMS, list.size).clear()
        saveAll(context, list)
        Log.i(TAG, "Kaydedildi: ${record.id} — ${record.summary}")
        return record
    }

    //----------

    fun updateMeasurement(context: Context, id: String, area: Double, width: Double, height: Double) {
        val list = loadAll(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index].woundArea = area
            list[index].woundWidth = width
            list[index].woundHeight = height
            saveAll(context, list)
            Log.i(TAG, "Ölçüm güncellendi: ${list[index].id}")
        }
    }

    // ── Tümünü yükle ─────────────────────────────────────────────────────
    fun loadAll(context: Context): List<AnalysisRecord> {
        return try {
            val file = historyFile(context)
            if (!file.exists()) return emptyList()
            val arr  = JSONArray(file.readText())
            (0 until arr.length()).map { parseRecord(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Yükleme hatası: ${e.message}")
            emptyList()
        }
    }

    // ── Kullanıcıya göre filtrele ─────────────────────────────────────────
    fun loadForUser(context: Context, username: String): List<AnalysisRecord> =
        loadAll(context).filter { it.username == username }

    // ── Sil ──────────────────────────────────────────────────────────────
    fun delete(context: Context, id: String) {
        val list = loadAll(context).filter { it.id != id }
        saveAll(context, list)
    }

    fun clearAll(context: Context) {
        historyFile(context).delete()
    }

    // ── İstatistik ────────────────────────────────────────────────────────
    data class Stats(
        val total       : Int,
        val woundCount  : Int,
        val noWoundCount: Int,
        val stageBreakdown : Map<Int, Int>,   // stage → count
        val riskBreakdown  : Map<Int, Int>    // riskLevel → count
    )

    fun stats(context: Context, username: String? = null): Stats {
        val list = if (username != null) loadForUser(context, username) else loadAll(context)
        val wounds    = list.filter { it.hasWound }
        val noWounds  = list.filter { !it.hasWound }
        return Stats(
            total        = list.size,
            woundCount   = wounds.size,
            noWoundCount = noWounds.size,
            stageBreakdown = wounds.groupBy { it.stage ?: -1 }.mapValues { it.value.size },
            riskBreakdown  = noWounds.groupBy { it.riskLevel ?: -1 }.mapValues { it.value.size }
        )
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────
    private fun historyFile(context: Context) =
        File(context.filesDir, FILE_NAME)

    private fun saveAll(context: Context, list: List<AnalysisRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        historyFile(context).writeText(arr.toString())
    }

    private fun toJson(r: AnalysisRecord) = JSONObject().apply {
        put("id",           r.id)
        put("date",         r.date)
        put("timestamp",    r.timestamp)
        put("username",     r.username)
        put("imageUri",     r.imageUri)
        put("hasWound",     r.hasWound)
        put("presenceConf", r.presenceConf)
        r.stage?.let       { put("stage",     it) }
        r.stageConf?.let   { put("stageConf", it) }
        r.stageName?.let   { put("stageName", it) }
        r.riskLevel?.let   { put("riskLevel", it) }
        r.riskConf?.let    { put("riskConf",  it) }
        r.riskName?.let    { put("riskName",  it) }
        r.woundArea?.let { put("woundArea", it) }
        r.woundWidth?.let { put("woundWidth", it) }
        r.woundHeight?.let { put("woundHeight", it) }
    }

    private fun parseRecord(o: JSONObject) = AnalysisRecord(
        id           = o.getString("id"),
        date         = o.getString("date"),
        timestamp    = o.getLong("timestamp"),
        username     = o.optString("username", ""),
        imageUri     = o.optString("imageUri", ""),
        hasWound     = o.getBoolean("hasWound"),
        presenceConf = o.getDouble("presenceConf").toFloat(),
        stage        = if (o.has("stage"))     o.getInt("stage")               else null,
        stageConf    = if (o.has("stageConf")) o.getDouble("stageConf").toFloat() else null,
        stageName    = if (o.has("stageName")) o.getString("stageName")         else null,
        riskLevel    = if (o.has("riskLevel")) o.getInt("riskLevel")            else null,
        riskConf     = if (o.has("riskConf"))  o.getDouble("riskConf").toFloat()  else null,
        riskName     = if (o.has("riskName"))  o.getString("riskName") else null,
        woundArea = if (o.has("woundArea")) o.getDouble("woundArea") else null,
        woundWidth = if (o.has("woundWidth")) o.getDouble("woundWidth") else null,
        woundHeight = if (o.has("woundHeight")) o.getDouble("woundHeight") else null
    )
}
