package com.local.comfyuimobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.service.JobMonitorService
import com.local.comfyuimobile.ui.ComfyMobileApp
import com.local.comfyuimobile.ui.ComfyMobileTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var bridge: ComfyBridge
    private var localResultsReceiverRegistered = false
    private var networkCallbackRegistered = false
    @Volatile private var currentDefaultNetwork: Network? = null
    @Volatile private var hasObservedDefaultNetwork = false
    @Volatile private var defaultNetworkWasLost = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = currentDefaultNetwork
            val changed = hasObservedDefaultNetwork && (defaultNetworkWasLost || previous != network)
            currentDefaultNetwork = network
            hasObservedDefaultNetwork = true
            defaultNetworkWasLost = false
            if (changed) viewModel.onNetworkAvailableAfterChange()
        }

        override fun onLost(network: Network) {
            if (currentDefaultNetwork != network) return
            currentDefaultNetwork = null
            defaultNetworkWasLost = true
            viewModel.onNetworkLost()
        }
    }

    private val localResultsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onLocalResultsSaved(
                count = intent.getIntExtra(JobMonitorService.EXTRA_SAVED_COUNT, 0),
                failed = intent.getBooleanExtra(JobMonitorService.EXTRA_SAVE_FAILED, false),
                localSaveRequested = intent.getBooleanExtra(JobMonitorService.EXTRA_LOCAL_SAVE_REQUESTED, false),
                promptId = intent.getStringExtra(JobMonitorService.EXTRA_PROMPT_ID).orEmpty(),
                serverUrl = intent.getStringExtra(JobMonitorService.EXTRA_BASE_URL).orEmpty(),
                executionFailed = intent.getBooleanExtra(JobMonitorService.EXTRA_EXECUTION_FAILED, false),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = ComfyBridge(this).also { it.configure() }
        viewModel.attachBridge(bridge)
        requestRuntimePermissions()
        registerLocalResultsReceiver()
        registerNetworkCallback()
        setContent {
            ComfyMobileTheme {
                ComfyMobileApp(viewModel, bridge)
            }
        }
        handleJobNotification(intent)
        viewModel.checkUpdate(manual = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleJobNotification(intent)
    }

    override fun onStop() {
        viewModel.persistCurrentWorkflowDraft()
        super.onStop()
    }

    override fun onDestroy() {
        if (localResultsReceiverRegistered) unregisterReceiver(localResultsReceiver)
        if (networkCallbackRegistered) {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        bridge.destroy()
        super.onDestroy()
    }

    private fun registerLocalResultsReceiver() {
        val filter = IntentFilter(JobMonitorService.ACTION_LOCAL_RESULTS_UPDATED)
        ContextCompat.registerReceiver(this, localResultsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        localResultsReceiverRegistered = true
    }

    private fun registerNetworkCallback() {
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun handleJobNotification(intent: Intent?) {
        if (intent?.action != JobMonitorService.ACTION_OPEN_JOB) return
        viewModel.openJobNotification(
            baseUrl = intent.getStringExtra(JobMonitorService.EXTRA_BASE_URL).orEmpty(),
            workflowPath = intent.getStringExtra(JobMonitorService.EXTRA_WORKFLOW_PATH).orEmpty(),
            promptId = intent.getStringExtra(JobMonitorService.EXTRA_PROMPT_ID).orEmpty(),
            completed = intent.getBooleanExtra(JobMonitorService.EXTRA_OPEN_COMPLETED, false),
        )
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 8100)
    }
}
