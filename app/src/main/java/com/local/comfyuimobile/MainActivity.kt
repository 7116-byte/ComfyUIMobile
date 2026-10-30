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
import com.local.comfyuimobile.data.AppLogger
import com.local.comfyuimobile.service.JobMonitorService
import com.local.comfyuimobile.ui.ComfyMobileApp
import com.local.comfyuimobile.ui.ComfyMobileTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val STATE_CONNECTED_SERVER = "connected_server_url"
    }
    private val viewModel: MainViewModel by viewModels()
    private lateinit var bridge: ComfyBridge
    private var localResultsReceiverRegistered = false
    private var networkCallbackRegistered = false
    private var ignoredLauncherDuplicate = false
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
        // Some launchers create another MAIN activity above the existing task,
        // especially when the first entry was a notification or an adb launch.
        // Keep the existing activity and its ViewModel/working copy in that case.
        if (!isTaskRoot && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            ignoredLauncherDuplicate = true
            AppLogger.info("复用现有应用任务，关闭重复的桌面入口：task=$taskId")
            finish()
            return
        }
        AppLogger.info("主页面创建：task=$taskId，root=$isTaskRoot，恢复=${savedInstanceState != null}")
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
        if (intent.action == JobMonitorService.ACTION_OPEN_JOB) {
            handleJobNotification(intent)
        } else {
            // Rebuilding an Activity also rebuilds its WebView. Reattach the
            // retained working copy, or restore only the saved connection after
            // process recreation. A fresh launch still opens the connection page.
            val previousServer = viewModel.state.value.activeServer?.baseUrl
                ?: savedInstanceState?.getString(STATE_CONNECTED_SERVER)
            if (!previousServer.isNullOrBlank()) viewModel.connect(previousServer)
        }
        viewModel.checkUpdate(manual = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::bridge.isInitialized && !ignoredLauncherDuplicate) {
            outState.putString(STATE_CONNECTED_SERVER, viewModel.state.value.activeServer?.baseUrl)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLogger.info("主页面复用：task=$taskId，action=${intent.action.orEmpty()}")
        setIntent(intent)
        handleJobNotification(intent)
    }

    override fun onStop() {
        if (!ignoredLauncherDuplicate) viewModel.persistCurrentWorkflowDraft()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized && !ignoredLauncherDuplicate) viewModel.onAppForegrounded()
    }

    override fun onDestroy() {
        if (localResultsReceiverRegistered) unregisterReceiver(localResultsReceiver)
        if (networkCallbackRegistered) {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        if (::bridge.isInitialized) bridge.destroy()
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
