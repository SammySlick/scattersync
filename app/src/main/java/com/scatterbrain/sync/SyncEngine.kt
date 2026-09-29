package com.scatterbrain.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reads Health Connect (paginated — ALL pages, no 1,000 cap) and uploads
 * to the HCGateway-compatible server. Backfills 30 days on first sync per type.
 */
object SyncEngine {

    val typeClasses: Map<String, kotlin.reflect.KClass<out Record>> = mapOf(
        "HeartRate" to HeartRateRecord::class,
        "Steps" to StepsRecord::class,
        "SleepSession" to SleepSessionRecord::class,
        "Weight" to WeightRecord::class,
        "BodyFat" to BodyFatRecord::class,
        "ExerciseSession" to ExerciseSessionRecord::class,
        "TotalCaloriesBurned" to TotalCaloriesBurnedRecord::class,
        "RestingHeartRate" to RestingHeartRateRecord::class,
        "BasalMetabolicRate" to BasalMetabolicRateRecord::class,
        "Nutrition" to NutritionRecord::class
    )

    suspend fun run(ctx: Context, manual: Boolean): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var token: String? = Prefs.token(ctx)
        if (token == null || token.isEmpty()) {
            token = try { ApiClient.login(ctx) } catch (e: Exception) {
                return@withContext "Login failed: ${e.message} (check server URL, username, password)"
            }
            Prefs.setToken(ctx, token)
        }
        val tok = token!!

        val client = HealthConnectClient.getOrCreate(ctx)
        val now = Instant.now()
        val sb = StringBuilder("Sync complete:\n")

        for ((methodName, kclass) in typeClasses) {
            val last = Prefs.lastSyncMs(ctx, methodName)
            val start = if (last == 0L) now.minus(30, ChronoUnit.DAYS) else Instant.ofEpochMilli(last)
            val records = readAll(client, kclass, start, now)
            if (records.isEmpty()) {
                sb.append("$methodName: 0 new\n")
                continue
            }
            var uploaded = 0
            var ok = true
            for (chunk in records.chunked(50)) {
                val arr = JSONArray()
                for (r in chunk) arr.put(toJson(r, methodName))
                val err = ApiClient.sync(ctx, methodName, arr.toString(), tok)
                if (err != null) { sb.append("$methodName chunk failed: $err\n"); ok = false; break }
                uploaded += chunk.size
            }
            if (ok) {
                Prefs.setLastSyncMs(ctx, methodName, now.toEpochMilli())
                sb.append("$methodName: $uploaded uploaded\n")
            } else {
                sb.append("$methodName: ERROR after $uploaded (will retry next sync)\n")
            }
        }
        sb.toString()
    }

    private suspend fun readAll(
        client: HealthConnectClient,
        kclass: kotlin.reflect.KClass<out Record>,
        start: Instant,
        end: Instant
    ): List<Record> {
        val all = mutableListOf<Record>()
        var pageToken: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = kclass,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = 1000,
                    pageToken = pageToken
                )
            )
            all.addAll(resp.records)
            pageToken = resp.pageToken
        } while (pageToken != null)
        return all
    }

    private fun iso(i: Instant): String =
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(i)

    private fun metaJson(r: Record): JSONObject = JSONObject().apply {
        put("id", r.metadata.id)
        put("dataOrigin", JSONObject().put("dataSourceId", r.metadata.dataOrigin))
        put("clientRecordId", r.metadata.clientRecordId ?: JSONObject.NULL)
        put("lastModifiedTime", iso(r.metadata.lastModifiedTime))
    }

    private fun energyJson(e: Energy): JSONObject =
        JSONObject().put("inKilocalories", e.inKilocalories)

    private fun toJson(r: Record, methodName: String): JSONObject {
        val o = JSONObject()
        o.put("type", methodName)
        o.put("metadata", metaJson(r))
        when (r) {
            is HeartRateRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                val s = JSONArray()
                for (x in r.samples) s.put(JSONObject()
                    .put("time", iso(x.time)).put("beatsPerMinute", x.beatsPerMinute))
                o.put("samples", s)
            }
            is StepsRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                o.put("count", r.count)
            }
            is SleepSessionRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                val s = JSONArray()
                for (x in r.stages) s.put(JSONObject()
                    .put("startTime", iso(x.startTime)).put("endTime", iso(x.endTime))
                    .put("stage", x.stage))
                o.put("stages", s)
                if (r.title != null) o.put("title", r.title)
                if (r.notes != null) o.put("notes", r.notes)
            }
            is WeightRecord -> {
                o.put("time", iso(r.time))
                o.put("weight", JSONObject().put("inKilograms", r.weight.inKilograms))
            }
            is BodyFatRecord -> {
                o.put("time", iso(r.time))
                o.put("percentage", r.percentage)
            }
            is ExerciseSessionRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                o.put("exerciseType", r.exerciseType)
                if (r.title != null) o.put("title", r.title)
            }
            is TotalCaloriesBurnedRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                o.put("energy", energyJson(r.energy))
            }
            is RestingHeartRateRecord -> {
                o.put("time", iso(r.time))
                o.put("beatsPerMinute", r.beatsPerMinute)
            }
            is BasalMetabolicRateRecord -> {
                o.put("time", iso(r.time))
                o.put("basalMetabolicRate", JSONObject().put("inWatts", r.basalMetabolicRate.inWatts))
            }
            is NutritionRecord -> {
                o.put("startTime", iso(r.startTime)); o.put("endTime", iso(r.endTime))
                if (r.name != null) o.put("name", r.name)
                o.put("mealType", r.mealType)
                val nutrients = JSONObject()
                r.energy?.let { nutrients.put("energy", energyJson(it)) }
                r.protein?.let { nutrients.put("protein", JSONObject().put("inGrams", it.inGrams)) }
                r.totalCarbohydrate?.let { nutrients.put("carbohydrates", JSONObject().put("inGrams", it.inGrams)) }
                r.totalFat?.let { nutrients.put("fat", JSONObject().put("inGrams", it.inGrams)) }
                r.sugar?.let { nutrients.put("sugar", JSONObject().put("inGrams", it.inGrams)) }
                r.dietaryFiber?.let { nutrients.put("fiber", JSONObject().put("inGrams", it.inGrams)) }
                r.sodium?.let { nutrients.put("sodium", JSONObject().put("inGrams", it.inGrams)) }
                o.put("nutrients", nutrients)
            }
            else -> throw IllegalArgumentException("Unhandled record type $methodName")
        }
        return o
    }
}
