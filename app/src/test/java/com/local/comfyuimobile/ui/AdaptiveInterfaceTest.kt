package com.local.comfyuimobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.WorkflowDocument
import com.local.comfyuimobile.model.WorkflowEntry
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class AdaptiveInterfaceTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var root: ViewRootForTest
    private var connectedAddress = ""
    private var scans = 0
    private var settings = false

    private val server = ServerProfile("studio", "我的工作站", "http://192.168.1.20:8188")
    private val entries = listOf(
        WorkflowEntry("图生图", "workflows/图生图", true),
        WorkflowEntry("人像精修.json", "workflows/人像精修.json", false, 24576),
        WorkflowEntry("产品摄影.json", "workflows/产品摄影.json", false, 18432),
        WorkflowEntry("千问多图.json", "workflows/千问多图.json", false, 32768),
    )

    @After fun tearDown() {
        if (::controller.isInitialized) { controller.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun phone393UsesSingleColumnAndBottomNavigation() { checkLayouts("phone-393", false) }

    @Test @Config(qualifiers = "w430dp-h932dp-night-mdpi")
    fun phone430DarkKeepsActionsVisible() { checkLayouts("phone-430-dark", false) }

    @Test @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun smallPhoneWithLargeTextKeepsConnectionActionUsable() {
        RuntimeEnvironment.setFontScale(1.3f)
        checkLayouts("phone-320-large-text", false)
    }

    @Test @Config(qualifiers = "w776dp-h1163dp-mdpi")
    fun tabletPortraitUsesTwoColumnsAndSideNavigation() { checkLayouts("tablet-portrait", true) }

    @Test @Config(qualifiers = "w1163dp-h776dp-land-night-mdpi")
    fun tabletLandscapeDarkUsesAvailableWidth() { checkLayouts("tablet-landscape-dark", true) }

    private fun checkLayouts(name: String, expanded: Boolean) {
        show(false)
        val heading = text(if (expanded) "连接你的\nComfyUI 工作站" else "连接你的创作空间")
        val form = text("添加连接")
        if (expanded) assertTrue(form.boundsInRoot.left > heading.boundsInRoot.left)
        else assertTrue(form.boundsInRoot.top > heading.boundsInRoot.top)
        click("连接工作站")
        assertEquals(server.baseUrl, connectedAddress)
        click("扫描局域网")
        assertEquals(1, scans)
        screenshot("$name-connection")
        show(true)
        val selectedPanel = text("当前选择")
        val selectedRow = text("人像精修", clickable = true)
        if (expanded) assertTrue(selectedPanel.boundsInRoot.left > selectedRow.boundsInRoot.right)
        else assertTrue(selectedPanel.boundsInRoot.top < selectedRow.boundsInRoot.top)
        assertTrue(text("打开参数").boundsInRoot.width > 0f)
        assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "删除工作流" } == true })
        screenshot("$name-workflows")
    }

    private fun show(connected: Boolean) {
        if (!::controller.isInitialized) {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            activity = controller.get()
        }
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        val state = AppUiState(
            serverInput = server.baseUrl, savedServers = listOf(server, server.copy(id = "remote", name = "远程工作站", baseUrl = "http://100.64.0.10:8188")),
            activeServer = if (connected) server else null,
            status = if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            bridgeReady = connected, workflows = entries,
            previewWorkflow = if (connected) WorkflowDocument(entries[1], "{}", emptyList()) else null,
        )
        activity.setContent {
            ComfyMobileTheme {
                if (connected) ConnectedApp(state, viewModel, SnackbarHostState()) { settings = true }
                else ConnectionScreen(state, SnackbarHostState(), { settings = true }, {}, { connectedAddress = it }, { scans++ }, {})
            }
        }
        settle()
        root = findRoot(activity.window.decorView) ?: error("Compose root missing")
    }

    private fun settle() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val decor = activity.window.decorView
            val config = activity.resources.configuration
            val density = activity.resources.displayMetrics.density
            val width = (config.screenWidthDp * density).toInt()
            val height = (config.screenHeightDp * density).toInt()
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, width, height)
            findRoot(decor)?.measureAndLayoutForTest()
        }
    }

    private fun findRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findRoot(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun nodes(node: SemanticsNode = root.semanticsOwner.rootSemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { nodes(it) }
    private fun text(value: String, clickable: Boolean = false): SemanticsNode = nodes().firstOrNull {
        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == value } == true && (!clickable || it.config.contains(SemanticsActions.OnClick))
    } ?: error("Text missing: $value")
    private fun click(value: String) { assertTrue(text(value, true).config[SemanticsActions.OnClick].action!!.invoke()); settle() }

    private fun screenshot(name: String) {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/outputs/adaptive-preview/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
