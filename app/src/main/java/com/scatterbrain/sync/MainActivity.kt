package com.scatterbrain.sync

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    private val permissionRequest =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
            lifecycleScope.launch { refreshStatus() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            setPadding(48, 48, 48, 24)
            textSize = 14f
            text = "Starting…"
        }

        val urlField = EditText(this).apply { hint = "Server URL"; setText(Prefs.server(this@MainActivity)) }
        val userField = EditText(this).apply { hint = "Username"; setText(Prefs.username(this@MainActivity)) }
        val passField = EditText(this).apply { hint = "Password"; setText(Prefs.password(this@MainActivity)) }

        val grantBtn = Button(this).apply { text = "Grant Health Connect permissions" }
        val saveBtn = Button(this).apply { text = "Save settings" }
        val syncBtn = Button(this).apply { text = "Sync now" }
        val schedBtn = Button(this).apply { text = "Enable 30-min background sync" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        root.addView(TextView(this).apply {
            text = "ScatterSync"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 8)
        })
        root.addView(status)
        root.addView(grantBtn)
        root.addView(TextView(this).apply {
            text = "Server settings"
            textSize = 12f
            setPadding(48, 32, 48, 4)
        })
        for (f in listOf(urlField, userField, passField)) {
            root.addView(f, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 0, 32, 0) })
        }
        for (b in listOf(saveBtn, syncBtn, schedBtn)) {
            root.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 16, 32, 0) })
        }
        setContentView(root)

        fun saveFields() {
            Prefs.setServer(this@MainActivity, urlField.text.toString().trim())
            Prefs.setUsername(this@MainActivity, userField.text.toString().trim())
            Prefs.setPassword(this@MainActivity, passField.text.toString())
            Prefs.setToken(this@MainActivity, "")
        }

        grantBtn.setOnClickListener {
            permissionRequest.launch(PERMISSIONS)
        }

        saveBtn.setOnClickListener {
            saveFields()
            updateStatus("Settings saved.")
            lifecycleScope.launch { refreshStatus() }
        }

        syncBtn.setOnClickListener {
            saveFields()
            updateStatus("Syncing… (this can take a while on first run)")
            lifecycleScope.launch {
                val summary = try {
                    SyncEngine.run(this@MainActivity, manual = true)
                } catch (e: Exception) {
                    "Sync error: ${e.message}"
                }
                updateStatus(summary)
            }
        }

        schedBtn.setOnClickListener {
            Scheduler.ensure(this)
            updateStatus(status.text.toString() + "\nBackground sync scheduled every 30 min.")
        }

        lifecycleScope.launch { refreshStatus() }
    }

    private fun updateStatus(s: String) {
        status.text = buildString {
            append(s)
            append("\n\nLast sync per type:")
            for (m in SyncEngine.typeClasses.keys) {
                val ms = Prefs.lastSyncMs(this@MainActivity, m)
                val t = if (ms == 0L) "never" else DateFormat.getDateTimeInstance().format(Date(ms))
                append("\n  $m: $t")
            }
        }
    }

    private suspend fun refreshStatus() {
        val client = HealthConnectClient.getOrCreate(this@MainActivity)
        val granted = HealthPermission.getGrantedPermissions(client)
        val missing = PERMISSIONS.filterNot { it in granted }
        val permLine = if (missing.isEmpty())
            "All Health Connect permissions granted."
        else "Missing ${missing.size} permissions — tap Grant."
        status.text = "$permLine\nServer: ${Prefs.server(this@MainActivity)}"
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class)
        )
    }
}
