package com.stepadder.app

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {

    private var client: HealthConnectClient? = null
    private var hasPermission = false

    // In-memory only. Nothing is saved to disk.
    private var pending = 0L
    private var pickedEnd: LocalTime? = null // null = "now"
    private var lastWrittenIds: List<String> = emptyList()
    private var lastWrittenCount = 0L

    private lateinit var status: TextView
    private lateinit var grantButton: Button
    private lateinit var pendingCount: TextView
    private lateinit var customAmount: EditText
    private lateinit var windowPreview: TextView
    private lateinit var writeButton: Button
    private lateinit var undoButton: Button
    private lateinit var result: TextView

    private val numberFormat = NumberFormat.getIntegerInstance()
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            hasPermission = granted.containsAll(StepWriter.PERMISSIONS)
            render()
        }

        status = findViewById(R.id.status)
        grantButton = findViewById(R.id.grantButton)
        pendingCount = findViewById(R.id.pendingCount)
        customAmount = findViewById(R.id.customAmount)
        windowPreview = findViewById(R.id.windowPreview)
        writeButton = findViewById(R.id.writeButton)
        undoButton = findViewById(R.id.undoButton)
        result = findViewById(R.id.result)

        findViewById<Button>(R.id.plus100).setOnClickListener { addPending(100) }
        findViewById<Button>(R.id.plus500).setOnClickListener { addPending(500) }
        findViewById<Button>(R.id.plus1000).setOnClickListener { addPending(1_000) }
        findViewById<Button>(R.id.plus5000).setOnClickListener { addPending(5_000) }
        findViewById<Button>(R.id.plusCustom).setOnClickListener {
            val n = customAmount.text.toString().toLongOrNull()
            if (n != null && n > 0) addPending(n)
            customAmount.text.clear()
        }
        findViewById<Button>(R.id.clearPending).setOnClickListener { pending = 0; render() }
        findViewById<Button>(R.id.endNow).setOnClickListener { pickedEnd = null; render() }
        findViewById<Button>(R.id.endPick).setOnClickListener { pickEndTime() }
        findViewById<TextView>(R.id.privacyLink).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        grantButton.setOnClickListener { permissionLauncher.launch(StepWriter.PERMISSIONS) }
        writeButton.setOnClickListener { writeSteps() }
        undoButton.setOnClickListener { undoLast() }

        render()
    }

    override fun onResume() {
        super.onResume()
        refreshHealthConnectState()
    }

    private fun refreshHealthConnectState() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            client = null
            hasPermission = false
            render()
            return
        }
        val c = client ?: HealthConnectClient.getOrCreate(this).also { client = it }
        lifecycleScope.launch {
            hasPermission = runCatching {
                c.permissionController.getGrantedPermissions().containsAll(StepWriter.PERMISSIONS)
            }.getOrDefault(false)
            render()
        }
    }

    private fun addPending(n: Long) {
        pending = (pending + n).coerceAtMost(StepPlanner.MAX_STEPS_PER_WRITE)
        render()
    }

    private fun pickEndTime() {
        val initial = pickedEnd ?: LocalTime.now()
        TimePickerDialog(
            this,
            { _, h, m -> pickedEnd = LocalTime.of(h, m); render() },
            initial.hour, initial.minute, DateFormat.is24HourFormat(this)
        ).show()
    }

    /** End of the walk. A picked time is today; a time later than now is rejected. */
    private fun resolveEnd(): Instant? {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val picked = pickedEnd ?: return now
        val end = LocalDate.now(zone).atTime(picked).atZone(zone).toInstant()
        return if (end.isAfter(now)) null else end
    }

    private fun render() {
        pendingCount.text = numberFormat.format(pending)

        status.text = when {
            client == null -> "Health Connect isn't available. Update the Health Connect " +
                "system module (Settings → Security & privacy → System & updates) and reopen."
            !hasPermission -> "StepAdder needs one permission: write steps to Health Connect."
            else -> "Ready. Only permission granted: write steps."
        }
        grantButton.visibility = if (client != null && !hasPermission) View.VISIBLE else View.GONE

        val end = resolveEnd()
        windowPreview.text = when {
            end == null -> "That time is later than now. Pick an earlier time today."
            pending == 0L -> "Ends ${if (pickedEnd == null) "now" else "at " + timeFormat.format(pickedEnd)}."
            else -> {
                val zone = ZoneId.systemDefault()
                val start = StepPlanner.estimatedStart(pending, end)
                "Logged as a walk from about ${timeFormat.format(start.atZone(zone))} " +
                    "to ${timeFormat.format(end.atZone(zone))}, minute by minute."
            }
        }

        writeButton.isEnabled = client != null && hasPermission && pending > 0 && end != null
        undoButton.isEnabled = client != null && lastWrittenIds.isNotEmpty()
        undoButton.text = if (lastWrittenIds.isEmpty()) "Undo last write"
        else "Undo last write (${numberFormat.format(lastWrittenCount)} steps)"
    }

    private fun writeSteps() {
        val c = client ?: return
        val end = resolveEnd() ?: return
        val total = pending
        writeButton.isEnabled = false
        lifecycleScope.launch {
            runCatching { StepWriter.write(c, total, end) }
                .onSuccess { ids ->
                    lastWrittenIds = ids
                    lastWrittenCount = total
                    pending = 0
                    pickedEnd = null
                    result.text = "Wrote ${numberFormat.format(total)} steps " +
                        "as automatically recorded phone data (${ids.size} records)."
                }
                .onFailure { e -> result.text = "Couldn't write steps: ${e.message}" }
            render()
        }
    }

    private fun undoLast() {
        val c = client ?: return
        val ids = lastWrittenIds
        if (ids.isEmpty()) return
        undoButton.isEnabled = false
        lifecycleScope.launch {
            runCatching { StepWriter.undo(c, ids) }
                .onSuccess {
                    result.text = "Removed ${numberFormat.format(lastWrittenCount)} steps."
                    lastWrittenIds = emptyList()
                    lastWrittenCount = 0
                }
                .onFailure { e -> result.text = "Couldn't undo: ${e.message}" }
            render()
        }
    }
}
