package com.scatterbrain.sync

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var syncButton: Button
    private var requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
            checkPermissionsAndPrompt()
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
        syncButton = Button(this).apply { text = "Grant Health Connect permissions" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val scroll = ScrollView(this).apply { addView(status) }
        fun addField(f: EditText, label: String) {
            val lbl = TextView(this).apply { text = label; setPadding(48, 24, 48, 4); textSize = 12f }
            root.addView(lbl)
            root.addView(f, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(32, 0, 32, 0)
            })
        }
        addField(urlField, "Server URL")
        addField(userField, "Username")
        addField(passField, "Password")

        val save = Button(this).apply { text = "Save settings" }
        val syncNow = Button(this).apply { text = "Sync now" }
        val schedule = Button(this).apply { text = "Enable 30-min background sync" }

        fun rows() {
            root.removeAllViewsInLayout()
            root.addView(TextView(this).apply {
                text = "ScatterSync"; textSize = 20f; setPadding(48, 48, 48, 8); gravity = Gravity.CENTER
            })
            root.addView(status)
            root.addView(syncButton)
            root.addView(TextView(this).apply { text = "Server settings"; setPadding(48, 32, 48, 4); textSize = 12f })
            root.addView(urlField, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 32, 0) })
            root.addView(userField, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 32, 0) })
            root.addView(passField, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 32, 0) })
            root.addView(save, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 16, 32, 0) })
            root.addView(syncNow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 32, 0) })
            root.addView(schedule, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 32, 32) })
        }
        rows()

        save.setOnClickListener {
            Prefs.server = urlField.text.toString().trim()
            Prefs.username = userField.text.toString().trim()
            Prefs.password = passField.text.toString()
            updateStatus("Settings saved. Token will be created on next sync.")
        }
        syncNow.setOnClickListener {
            Prefs.server = urlField.text.toString().trim()
            Prefs.username = userField.text.toString().trim()
            Prefs.password = passField.text.toString()
            updateStatus("Syncing…")
            lifecycleScope.launch {
                val summary = SyncEngine.run(this@MainActivity, manual = true)
                updateStatus(summary)
            }
        }
        schedule.setOnClickListener {
            Scheduler.ensure(this)
            updateStatus(status.text.toString() + "\nBackground sync scheduled every 30 min.")
        }
        syncButton.setOnClickListener {
            requestPermissions.launch(HealthPermission.PERMISSIONS)
        }

        lifecycleScope.launch { checkPermissionsAndPrompt() }
    }

    private fun updateStatus(s: String) {
        runOnUiThread { status.text = buildString {
            append(s)
            append("\n\nLast sync per type:")
            append(SyncEngine.lastSyncSummary())
        } }
    }

    private suspend fun checkPermissionsAndPrompt() {
        val granted = HealthPermission.getGrantedPermissions(this)
        val missing = HealthPermission.PERMISSIONS.filterNot { it in granted }
        status.text = if (missing.isEmpty())
            "All Health Connect permissions granted.\nServer: ${Prefs.server}"
        else "Missing ${missing.size} permissions — tap the button below.\nServer: ${Prefs.server}"
    }
}
