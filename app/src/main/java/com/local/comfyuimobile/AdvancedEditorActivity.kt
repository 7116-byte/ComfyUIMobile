package com.local.comfyuimobile

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.local.comfyuimobile.bridge.AdvancedEditorSession
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.data.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AdvancedEditorActivity : ComponentActivity() {
    private lateinit var bridge: ComfyBridge
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var closeButton: Button
    private lateinit var webContainer: FrameLayout
    private var displayedWebView: WebView? = null
    private var pageReady = false
    private var closing = false
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(30, 30, 36)
        window.navigationBarColor = Color.rgb(18, 18, 22)

        bridge = ComfyBridge(this).also { it.configure() }
        setContentView(buildContentView())
        bridge.onWebViewRecreated = { replacement -> attachWebView(replacement) }
        onBackPressedDispatcher.addCallback(this) { closeEditor() }

        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty()
        val workflowJson = AdvancedEditorSession.input()
        if (serverUrl.isBlank() || workflowJson.isNullOrBlank()) {
            showError("缺少服务器地址或当前工作流，无法打开高级编辑。")
            return
        }

        loadJob = lifecycleScope.launch {
            runCatching {
                bridge.loadServer(serverUrl)
                bridge.awaitReady()
                bridge.loadWorkflow(workflowJson)
                bridge.refreshVisibleViewport()
            }.onSuccess {
                pageReady = true
                progress.visibility = View.GONE
                message.visibility = View.GONE
            }.onFailure { error ->
                AppLogger.error("高级编辑网页加载失败", error)
                showError("ComfyUI 网页加载失败：\n${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(32, 32, 32))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(42, 40, 50))
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBlock.addView(TextView(this).apply {
            text = "ComfyUI 网页编辑"
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        titleBlock.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty()
            textSize = 12f
            setTextColor(Color.LTGRAY)
            maxLines = 1
        })
        closeButton = Button(this).apply {
            text = "关闭并刷新参数"
            setOnClickListener { closeEditor() }
        }
        toolbar.addView(titleBlock)
        toolbar.addView(closeButton)

        webContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(32, 32, 32))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        attachWebView(bridge.webView)
        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        webContainer.addView(
            progress,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER),
        )
        message = TextView(this).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        webContainer.addView(
            message,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        root.addView(toolbar)
        root.addView(webContainer)
        return root
    }

    private fun attachWebView(value: WebView) {
        displayedWebView?.let { current ->
            (current.parent as? ViewGroup)?.removeView(current)
        }
        (value.parent as? ViewGroup)?.removeView(value)
        webContainer.addView(
            value,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        displayedWebView = value
    }

    private fun closeEditor() {
        if (closing) return
        if (!pageReady) {
            loadJob?.cancel()
            AdvancedEditorSession.clear()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        closing = true
        progress.visibility = View.VISIBLE
        closeButton.isEnabled = false
        lifecycleScope.launch {
            runCatching { bridge.exportCurrentWorkflow() }
                .onSuccess { workflowJson ->
                    AdvancedEditorSession.complete(workflowJson)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .onFailure { error ->
                    closing = false
                    progress.visibility = View.GONE
                    closeButton.isEnabled = true
                    AppLogger.error("高级编辑结果读取失败", error)
                    showError("读取网页工作流失败：\n${error.message ?: error.javaClass.simpleName}")
                }
        }
    }

    private fun showError(text: String) {
        progress.visibility = View.GONE
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bridge.webView.post {
            bridge.webView.requestLayout()
            bridge.webView.invalidate()
            bridge.webView.evaluateJavascript("window.dispatchEvent(new Event('resize'))", null)
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        bridge.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER_URL = "server_url"
    }
}
