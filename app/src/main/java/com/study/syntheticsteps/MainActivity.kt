package com.study.syntheticsteps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val STEP_INTERVAL_MS = 2_000L
    }

    private val writeStepsPermission = HealthPermission.getWritePermission(StepsRecord::class)
    private val requestedPermissions = setOf(writeStepsPermission)

    private var healthConnectClient: HealthConnectClient? = null
    private var generatorJob: Job? = null
    private var insertedSteps: Long = 0
    private var lastRecordEnd: Instant? = null

    private lateinit var healthStatusText: TextView
    private lateinit var permissionStatusText: TextView
    private lateinit var generatedCountText: TextView
    private lateinit var lastWriteText: TextView
    private lateinit var errorText: TextView
    private lateinit var permissionButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var openHealthConnectButton: Button

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        val granted = grantedPermissions.contains(writeStepsPermission)
        renderPermissionState(granted)
        if (!granted) {
            showError("Step write permission was not granted. You can grant it later from Health Connect.")
        } else {
            clearError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        wireButtons()
        initializeHealthConnect()
    }

    override fun onStop() {
        // Keep the study behavior explicit: synthetic generation stops when this UI leaves the foreground.
        if (generatorJob != null) {
            stopGenerator()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (healthConnectClient != null) {
            lifecycleScope.launch { refreshPermissionState() }
        }
    }

    override fun onDestroy() {
        generatorJob?.cancel()
        generatorJob = null
        super.onDestroy()
    }

    private fun bindViews() {
        healthStatusText = findViewById(R.id.healthStatusText)
        permissionStatusText = findViewById(R.id.permissionStatusText)
        generatedCountText = findViewById(R.id.generatedCountText)
        lastWriteText = findViewById(R.id.lastWriteText)
        errorText = findViewById(R.id.errorText)
        permissionButton = findViewById(R.id.permissionButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        openHealthConnectButton = findViewById(R.id.openHealthConnectButton)
    }

    private fun wireButtons() {
        permissionButton.setOnClickListener {
            if (healthConnectClient == null) {
                showError("Health Connect is not currently available on this phone.")
            } else {
                permissionLauncher.launch(requestedPermissions)
            }
        }

        startButton.setOnClickListener {
            lifecycleScope.launch {
                startGeneratorIfPermitted()
            }
        }

        stopButton.setOnClickListener {
            stopGenerator()
        }

        openHealthConnectButton.setOnClickListener {
            openHealthConnect()
        }
    }

    private fun initializeHealthConnect() {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnectClient = HealthConnectClient.getOrCreate(this)
                healthStatusText.text = "Health Connect status: available"
                lifecycleScope.launch { refreshPermissionState() }
            }

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                healthStatusText.text = "Health Connect status: install/update required"
                permissionStatusText.text = "Write permission: unavailable"
                disableGeneratorControls()
                showError("Install or update Health Connect, then reopen this app.")
            }

            else -> {
                healthStatusText.text = "Health Connect status: unavailable"
                permissionStatusText.text = "Write permission: unavailable"
                disableGeneratorControls()
                showError("Health Connect requires a supported Android phone with Google Play services.")
            }
        }
    }

    private suspend fun refreshPermissionState(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController
                .getGrantedPermissions()
                .contains(writeStepsPermission)
            renderPermissionState(granted)
            granted
        } catch (t: Throwable) {
            renderPermissionState(false)
            showError("Could not check Health Connect permission: ${t.message ?: t.javaClass.simpleName}")
            false
        }
    }

    private fun renderPermissionState(granted: Boolean) {
        permissionStatusText.text = if (granted) {
            "Write permission: granted"
        } else {
            "Write permission: not granted"
        }
        permissionButton.isEnabled = healthConnectClient != null && !granted
        startButton.isEnabled = healthConnectClient != null && granted && generatorJob == null
        stopButton.isEnabled = generatorJob != null
    }

    private suspend fun startGeneratorIfPermitted() {
        if (generatorJob != null) return

        if (!refreshPermissionState()) {
            permissionLauncher.launch(requestedPermissions)
            return
        }

        val client = healthConnectClient ?: return
        clearError()
        lastRecordEnd = Instant.now()
        startButton.isEnabled = false
        stopButton.isEnabled = true

        generatorJob = lifecycleScope.launch {
            try {
                while (isActive) {
                    delay(STEP_INTERVAL_MS)

                    val endTime = Instant.now()
                    val startTime = lastRecordEnd ?: endTime.minusMillis(STEP_INTERVAL_MS)

                    val device = Device(
                        type = Device.TYPE_PHONE,
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL
                    )

                    val record = StepsRecord(
                        count = 1,
                        startTime = startTime,
                        endTime = endTime,
                        startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                        endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                        metadata = Metadata.activelyRecorded(device = device)
                    )

                    client.insertRecords(listOf(record))
                    lastRecordEnd = endTime
                    insertedSteps += 1
                    updateInsertedCount(endTime)
                }
            } catch (security: SecurityException) {
                showError("Health Connect permission was revoked or denied. Stop the session and grant permission again.")
            } catch (t: Throwable) {
                showError("Write failed: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                generatorJob = null
                stopButton.isEnabled = false
                lifecycleScope.launch { refreshPermissionState() }
            }
        }
    }

    private fun stopGenerator() {
        generatorJob?.cancel()
        generatorJob = null
        lastRecordEnd = null
        stopButton.isEnabled = false

        lifecycleScope.launch {
            val granted = refreshPermissionState()
            startButton.isEnabled = healthConnectClient != null && granted
        }
    }

    private fun updateInsertedCount(lastWrite: Instant) {
        generatedCountText.text = "Steps inserted this session: $insertedSteps"
        val formatted = DateTimeFormatter.ISO_LOCAL_TIME
            .withZone(ZoneId.systemDefault())
            .format(lastWrite)
        lastWriteText.text = "Last write: $formatted"
    }

    private fun disableGeneratorControls() {
        permissionButton.isEnabled = false
        startButton.isEnabled = false
        stopButton.isEnabled = false
    }

    private fun openHealthConnect() {
        try {
            if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
                startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this))
            } else {
                openHealthConnectStorePage()
            }
        } catch (_: ActivityNotFoundException) {
            openHealthConnectStorePage()
        } catch (t: Throwable) {
            showError("Could not open Health Connect: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun openHealthConnectStorePage() {
        val marketUri = Uri.parse(
            "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        )
        val webUri = Uri.parse(
            "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"
        )

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, marketUri).apply {
                    setPackage("com.android.vending")
                }
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun clearError() {
        errorText.text = ""
        errorText.visibility = View.GONE
    }
}
