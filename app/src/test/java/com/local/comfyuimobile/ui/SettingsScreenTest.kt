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
import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ServerProfile
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class SettingsScreenTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var root: ViewRootForTest
    private var dismissed = false

    @After fun tearDown() {
        if (::controller.isInitialized) {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun disconnectedUpdateAndBackNavigationStayAvailable() {
        show(AppUiState())
        assertTrue(hasText("本地数据"))
        assertFalse(hasText("删除全部本地作品"))
        screenshot("settings-overview")
        click("软件更新")
        assertTrue(hasText("检查更新"))
        assertFalse(nodeWithText("检查更新").config.contains(SemanticsProperties.Disabled))
        screenshot("settings-update")
        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        assertTrue(hasText("本地数据"))
        assertFalse(dismissed)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(dismissed)
    }

    @Test fun emptyLocalResultsCannotBeDeleted() {
        show(AppUiState())
        click("本地作品")
        assertTrue(hasText("尚未添加保存规则"))
        assertTrue(nodeWithText("删除全部本地作品").config.contains(SemanticsProperties.Disabled))
        screenshot("settings-local-results")
    }

    @Test fun deletingLocalResultsRequiresConfirmationAndCancelKeepsPage() {
        val media = ResultMedia("job", "1", filename = "result.png", subfolder = "", type = "output", kind = MediaKind.IMAGE, url = "file:///result.png")
        show(AppUiState(localResults = listOf(media)))
        click("本地作品")
        click("删除全部本地作品")
        assertFalse(dismissed)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        val dialogRoot = findRoot(dialog.window!!.decorView) ?: error("Confirmation missing")
        val cancel = nodes(dialogRoot.semanticsOwner.rootSemanticsNode).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "取消" } == true
        }
        assertTrue(cancel.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
        assertFalse(dialog.isShowing)
        assertTrue(hasText("存储管理"))
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun darkSettingsShowConnectionAndRuleDetails() {
        show(AppUiState(
            status = ConnectionStatus.CONNECTED,
            activeServer = ServerProfile("server", "工作站", "http://192.168.1.20:8188"),
            cacheOutputRules = listOf(CacheOutputRule("http://192.168.1.20:8188", "SaveImage", "保存图像")),
        ))
        assertTrue(hasText("已连接 · 工作站"))
        screenshot("settings-overview-dark")
        click("本地作品")
        assertTrue(hasText("保存图像"))
        assertTrue(hasText("移除规则"))
        screenshot("settings-rules-dark")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowScreenAndLargeTextKeepLogActionsAccessible() {
        RuntimeEnvironment.setFontScale(1.3f)
        show(AppUiState())
        screenshot("settings-overview-large-text")
        click("诊断日志")
        assertTrue(hasText("查看日志"))
        assertTrue(hasText("导出日志"))
        screenshot("settings-logs-large-text")
    }

    private fun show(state: AppUiState) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        activity.setContent {
            ComfyMobileTheme {
                SettingsScreen(state, viewModel, SnackbarHostState()) { dismissed = true }
            }
        }
        settle()
        root = findRoot(activity.window.decorView) ?: error("Compose root missing")
        root.measureAndLayoutForTest()
    }

    private fun settle() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val decor = activity.window.decorView
            val configuration = activity.resources.configuration
            val density = activity.resources.displayMetrics.density
            val width = (configuration.screenWidthDp * density).toInt()
            val height = (configuration.screenHeightDp * density).toInt()
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, width, height)
            findRoot(decor)?.measureAndLayoutForTest()
        }
    }

    private fun findRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun nodes(node: SemanticsNode = root.semanticsOwner.rootSemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { nodes(it) }

    private fun hasText(text: String): Boolean = nodes().any { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    private fun nodeWithText(text: String): SemanticsNode = nodes().first { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    private fun click(text: String) {
        val action = nodeWithText(text).config.getOrNull(SemanticsActions.OnClick)?.action ?: error("No click action for $text")
        assertTrue(action())
        settle()
    }

    private fun screenshot(name: String) {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/outputs/settings-preview/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
