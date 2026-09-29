package com.scatterbrain.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object SyncEngine {

    private val typeClasses: List<Pair<String, Class<out Record>>> = listOf(
        "HeartRate" to HeartRateRecord::class.java,
        "Steps" to StepsRecord::class.java,
        "SleepSession" to SleepSessionRecord::class.java,
        "Weight" to WeightRecord::class.java,
        "BodyFat" to BodyFatRecord::class.java,
        "ExerciseSession" to ExerciseSessionRecord::class.java,
        "TotalCaloriesBurned" to TotalCaloriesBurnedRecord::class.java,
        "RestingHeartRate" to RestingHeartRateRecord::class.java,
        "BasalMetabolicRate" to BasalMetabolicRateRecord::class.java,
        "Nutrition" to NutritionRecord::class.java,
    )

    private const val PAGE = 5000      // max records per read page
    private const val CHUNK = 50       // records per upload POST

    suspend fun syncAll(ctx: Context, client: HealthConnectClient, token: String): String {
        val sb = StringBuilder()
        val now = Instant.now()
        for ((methodName, kclass) in typeClasses) {
            val last = Prefs.lastSyncMs(ctx, methodName)
            val start = if (last == 0L) now.minus(30, ChronoUnit.DAYS) else Instant.ofEpochMilli(last)
            // STREAMING: read one page, upload, discard. Never buffer the whole
            // month (OOM otherwise on minute-level HR backfills).
            var pageToken: String? = null
            var uploaded = 0
            var error: String? = null
            do {
                val resp = try {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = kclass,
                            timeRangeFilter = TimeRangeFilter.between(start, now),
                            pageSize = PAGE,
                            pageToken = pageToken
                        )
                    )
                } catch (e: Exception) {
                    error = classify(e, uploaded)
                    break
                }
                val jsons = resp.records.map { toJson(it, methodName).toString() }
                var i = 0
                while (i < jsons.size && error == null) {
                    val chunk = jsons.subList(i, minOf(i + CHUNK, jsons.size))
                    val arr = JSONArray()
                    for (s in chunk) arr.put(s)
                    val err = try {
                        ApiClient.sync(ctx, methodName, arr.toString(), token)
                    } catch (e: Exception) {
                        e.message ?: e.javaClass.simpleName
                    }
                    if (err != null) { error = err; break }
                    uploaded += chunk.size
                    i += CHUNK
                }
                pageToken = if (error == null) resp.pageToken else null
            } while (pageToken != null)
            if (error == null) {
                Prefs.setLastSyncMs(ctx, methodName, now.toEpochMilli())
                sb.append("$methodName: $uploaded uploaded\n")
            } else {
                // do NOT advance lastSync: the next sync resumes from the same point
                sb.append("$methodName: ERROR after $uploaded — $error\n")
            }
        }
        return sb.toString()
    }

    private fun classify(e: Exception, uploaded: Int): String {
        val m = e.message ?: return e.javaClass.simpleName
        return if (m.contains("Rate limited", true) || m.contains("quota", true)) {
            "HC hourly quota hit after $uploaded records — resumes next sync (quota replenishes hourly)"
        } else m
    }

    private fun meta(rec: Record): JSONObject {
        val md = JSONObject()
        md.put("id", rec.metadata.id)
        return md
    }

    private fun toJson(rec: Record, type: String): JSONObject {
        val o = JSONObject()
        o.put("metadata", meta(rec))
        when (rec) {
            is HeartRateRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                val d = JSONObject()
                val s = JSONArray()
                for (sample in rec.samples) {
                    s.put(JSONObject().put("time", iso(sample.time)).put("bpm", sample.beatsPerMinute))
                }
                d.put("samples", s); o.put("data", d)
            }
            is RestingHeartRateRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("bpm", rec.beatsPerMinute))
            }
            is StepsRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject().put("count", rec.count))
            }
            is SleepSessionRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject())
            }
            is WeightRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("kg", rec.weight.inKilograms))
            }
            is BodyFatRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("pct", rec.percentage.value))
            }
            is ExerciseSessionRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject().put("exerciseType", rec.exerciseType))
            }
            is TotalCaloriesBurnedRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject().put("kcal", rec.energy.inKilocalories))
            }
            is BasalMetabolicRateRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("watts", rec.basalMetabolicRate.inWatts))
            }
            is NutritionRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                val d = JSONObject()
                d.put("name", rec.name)
                rec.energy?.let { d.put("kcal", it.inKilocalories) }
                rec.protein?.let { d.put("protein", it.inGrams) }
                rec.totalCarbohydrate?.let { d.put("carbs", it.inGrams) }
                rec.totalFat?.let { d.put("fat", it.inGrams) }
                o.put("data", d)
            }
            else -> { o.put("data", JSONObject()) }
        }
        return o
    }

    private fun iso(t: Instant): String = t.toString()
}
