package com.scatterbrain.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

object SyncEngine {

    val typeClasses: Map<String, KClass<out Record>> = mapOf(
        "HeartRate" to HeartRateRecord::class,
        "Steps" to StepsRecord::class,
        "SleepSession" to SleepSessionRecord::class,
        "Weight" to WeightRecord::class,
        "BodyFat" to BodyFatRecord::class,
        "ExerciseSession" to ExerciseSessionRecord::class,
        "TotalCaloriesBurned" to TotalCaloriesBurnedRecord::class,
        "RestingHeartRate" to RestingHeartRateRecord::class,
        "BasalMetabolicRate" to BasalMetabolicRateRecord::class,
        "Nutrition" to NutritionRecord::class,
        "HRV" to HeartRateVariabilityRmssdRecord::class,
        "OxygenSaturation" to OxygenSaturationRecord::class,
        "RespiratoryRate" to RespiratoryRateRecord::class,
        "Vo2Max" to Vo2MaxRecord::class,
        "Distance" to DistanceRecord::class,
        "FloorsClimbed" to FloorsClimbedRecord::class,
    )

    private const val PAGE = 5000      // max records per read page
    private const val CHUNK = 50       // records per upload POST

    suspend fun run(ctx: Context, manual: Boolean = false): String {
        if (HealthConnectClient.getSdkStatus(ctx) != HealthConnectClient.SDK_AVAILABLE) {
            return "Health Connect not available — cannot sync."
        }
        if (Prefs.username(ctx).isBlank() || Prefs.password(ctx).isBlank()) {
            return "No server credentials — fill in username and password, Save settings, then Sync now."
        }
        val client = HealthConnectClient.getOrCreate(ctx)
        val sb = StringBuilder()
        val now = Instant.now()
        var token = Prefs.token(ctx) ?: ""
        if (token.isBlank()) {
            token = try {
                ApiClient.login(ctx)
            } catch (e: Exception) {
                return "Login failed: ${e.message} (check server URL, username, password)"
            }
            Prefs.setToken(ctx, token)
        }
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
                val d = JSONObject()
                if (rec.notes != null) d.put("notes", rec.notes)
                val st = JSONArray()
                for (g in rec.stages) {
                    st.put(JSONObject().put("startTime", iso(g.startTime)).put("endTime", iso(g.endTime)).put("stage", g.stage))
                }
                d.put("stages", st); o.put("data", d)
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
            is HeartRateVariabilityRmssdRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("rmssd", rec.heartRateVariabilityMillis))
            }
            is OxygenSaturationRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("pct", rec.percentage.value))
            }
            is RespiratoryRateRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("rate", rec.rate))
            }
            is Vo2MaxRecord -> {
                o.put("startTime", iso(rec.time)); o.put("endTime", iso(rec.time))
                o.put("data", JSONObject().put("vo2max", rec.vo2MillilitersPerMinuteKilogram))
            }
            is DistanceRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject().put("meters", rec.distance.inMeters))
            }
            is FloorsClimbedRecord -> {
                o.put("startTime", iso(rec.startTime)); o.put("endTime", iso(rec.endTime))
                o.put("data", JSONObject().put("floors", rec.floors))
            }
            else -> { o.put("data", JSONObject()) }
        }
        return o
    }

    private fun iso(t: Instant): String = t.toString()
}
