package com.local.comfyuimobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.WorkflowNode
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.SystemStats
import com.local.comfyuimobile.model.DeviceStats
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
import org.robolectric.shadows.ShadowDialog
import kotlinx.coroutines.launch

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
    private lateinit var viewModel: MainViewModel
    private lateinit var snackbar: SnackbarHostState
    private val fixtureBitmaps = mutableListOf<Bitmap>()
    private var fixtureLoader: ImageLoader? = null

    private val server = ServerProfile("studio", "我的工作站", "http://192.168.1.20:8188")
    private val entries = listOf(
        WorkflowEntry("图生图", "workflows/图生图", true),
        WorkflowEntry("人像精修.json", "workflows/人像精修.json", false, 24576),
        WorkflowEntry("产品摄影.json", "workflows/产品摄影.json", false, 18432),
        WorkflowEntry("千问多图.json", "workflows/千问多图.json", false, 32768),
    )

    @After fun tearDown() {
        if (::controller.isInitialized) { controller.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle() }
        fixtureLoader?.shutdown()
        Coil.reset()
        fixtureBitmaps.forEach { it.recycle() }
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
    fun tabletPortraitUsesTwoColumnsAndSideNavigation() {
        RuntimeEnvironment.setFontScale(1.25f)
        checkLayouts("tablet-portrait", true)
    }

    @Test @Config(qualifiers = "w1163dp-h776dp-land-night-mdpi")
    fun tabletLandscapeDarkUsesAvailableWidth() { checkLayouts("tablet-landscape-dark", true) }

    @Test @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun parameterFeedbackStaysInPinnedToolbarAndDockClearsLastCardOnSmallPhone() {
        RuntimeEnvironment.setFontScale(1.3f)
        show(true, longContent = true) { state -> state.copy(
            nodeProblems = mapOf("1" to listOf("输入参数不完整")),
            generationMessage = "生成完成，已保存 2 张作品", generationProgress = 1f,
        ) }
        click("参数")
        val error = text("创作提示词：输入参数不完整")
        val complete = text("生成完成，已保存 2 张作品")
        assertTrue(error.boundsInRoot.bottom < text("当前工作流").boundsInRoot.top)
        assertTrue(complete.boundsInRoot.bottom < text("当前工作流").boundsInRoot.top)
        val toolbarBounds = tagged("studio-toolbar").boundsInRoot
        screenshot("phone-320-parameter-feedback")
        scrollToEnd()
        assertTrue(isVisible("创作提示词：输入参数不完整"))
        assertTrue(isVisible("生成完成，已保存 2 张作品"))
        assertEquals(toolbarBounds, tagged("studio-toolbar").boundsInRoot)
        assertFalse(isVisible("当前工作流"))
        val lastNode = text("预览部件 24")
        val advanced = text("高级编辑", clickable = true)
        assertTrue(lastNode.boundsInRoot.height > 1f)
        assertTrue(lastNode.boundsInRoot.bottom < advanced.boundsInRoot.top)
        assertTrue(text("生成", clickable = true).boundsInRoot.width >= 48f)
        screenshot("phone-320-parameter-floating-end")
    }

    @Test fun parameterActionErrorsUseToolbarAndCanBeDismissed() {
        show(true) { it.copy(error = "保存失败，请检查连接") }
        click("参数")
        activity.lifecycleScope.launch { snackbar.showSnackbar("保存失败，请检查连接") }
        settle()
        val message = text("保存失败，请检查连接")
        assertTrue(message.boundsInRoot.bottom < text("当前工作流").boundsInRoot.top)
        screenshot("phone-393-parameter-action-error")
        clickDescription("关闭提示")
        assertNull(snackbar.currentSnackbarData)
    }

    @Test @Config(qualifiers = "w776dp-h1163dp-mdpi")
    fun tabletShowsValidationInToolbarAboveWorkflowSummary() {
        RuntimeEnvironment.setFontScale(1.25f)
        show(true) { it.copy(nodeProblems = mapOf("1" to listOf("模型选项已经失效"))) }
        click("参数")
        val warning = text("创作提示词：模型选项已经失效")
        assertTrue(warning.boundsInRoot.bottom < text("当前工作流").boundsInRoot.top)
        assertTrue(warning.boundsInRoot.width > 200f)
        screenshot("tablet-portrait-parameter-toolbar-error")
    }

    @Test fun clippedToolbarMessageCanOpenFullDetails() {
        val message = "生成失败：" + "连接中断，请检查服务器状态。".repeat(12)
        show(true) { it.copy(generationMessage = message) }
        click("参数")
        click(message)
        val dialog = ShadowDialog.getLatestDialog() ?: error("Feedback details missing")
        assertTrue(dialog.isShowing)
        val dialogRoot = findRoot(dialog.window!!.decorView) ?: error("Dialog root missing")
        assertTrue(nodes(dialogRoot.semanticsOwner.rootSemanticsNode).any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == message } == true
        })
        dismissDialog("关闭")
    }

    @Test fun floatingGenerateRetainsLocalOutputConfirmation() {
        show(true)
        click("参数")
        click("生成")
        dismissConfirmation()
        assertNull(viewModel.state.value.error)
    }

    @Test fun openParametersRemainsVisibleAfterWorkflowScrollAndFeedbackSurvivesTabChanges() {
        show(true, longContent = true) { it.copy(
            nodeProblems = mapOf("1" to listOf("模型选项已经失效")),
            generationMessage = "生成完成", generationProgress = 1f,
        ) }
        scrollToEnd()
        assertTrue(isVisible("打开参数"))
        assertTrue(isVisible("预览流程 024"))
        assertTrue(text("预览流程 024").boundsInRoot.bottom < text("打开参数", clickable = true).boundsInRoot.top)
        assertTrue(isVisible("创作提示词：模型选项已经失效"))
        click("结果")
        assertTrue(isVisible("生成完成"))
        assertTrue(isVisible("创作提示词：模型选项已经失效"))
        click("后台")
        assertTrue(isVisible("生成完成"))
        screenshot("phone-393-global-feedback")
        click("工作流")
        assertTrue(isVisible("打开参数"))
        screenshot("phone-393-workflow-floating-open")
    }

    @Test fun longPagesScrollPastControlsAndReachLastContent() {
        show(true, longContent = true)
        assertTrue(isVisible("搜索工作流"))
        scrollToEnd()
        assertFalse(isVisible("搜索工作流"))
        assertTrue(isVisible("预览流程 024"))

        click("参数")
        assertFalse(isVisible(server.baseUrl))
        assertTrue(isVisible("当前工作流"))
        scrollToEnd()
        assertFalse(isVisible("当前工作流"))
        assertTrue(isVisible("预览部件 24"))
        assertTrue(isVisible("生成"))

        click("结果")
        clickDescription("切换到全部平铺")
        scrollToEnd()
        assertFalse(isVisible("本地作品"))
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("sample-0.png") == true && it.boundsInRoot.height > 0f })

        click("后台")
        assertTrue(isVisible(server.baseUrl))
        click("服务器信息")
        assertTrue(isVisible("ComfyUI test-version"))
        click("收起服务器信息")
        scrollToEnd()
        assertFalse(isVisible(server.baseUrl))
        assertTrue(isVisible("后台任务 24"))
        screenshot("phone-393-scrolled-backend")
        click("工作流")
        assertFalse(isVisible("搜索工作流"))
        assertTrue(isVisible("预览流程 024"))
        click("参数")
        assertTrue(isVisible("预览部件 24"))
    }

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
        checkOtherPages(name, expanded)
    }

    private fun checkOtherPages(name: String, expanded: Boolean) {
        click("参数")
        assertTrue(text("保存").boundsInRoot.width > 0f)
        assertTrue(text("生成").boundsInRoot.width > 0f)
        click("创作提示词")
        assertTrue(text("提示词").boundsInRoot.width > 0f)
        if (expanded) assertTrue(text("流程目录").boundsInRoot.width > 0f)
        screenshot("$name-parameters")
        click("结果")
        assertTrue(text("本地作品").boundsInRoot.width > 0f)
        clickDescription("切换到全部平铺")
        assertTrue(text("全部作品").boundsInRoot.width > 0f)
        screenshot("$name-results")
        click("后台")
        assertTrue(isVisible(server.baseUrl))
        click("服务器信息")
        assertTrue(isVisible("ComfyUI test-version"))
        click("收起服务器信息")
        assertTrue(text("任务记录").boundsInRoot.width > 0f)
        click("取消任务")
        dismissConfirmation()
        assertNull(viewModel.state.value.error)
        click("清空等待")
        dismissConfirmation()
        assertNull(viewModel.state.value.error)
        screenshot("$name-backend")
        clickDescription("设置")
        if (expanded) assertTrue(text("设置概览").boundsInRoot.width > 0f)
        else assertTrue(text("本地数据").boundsInRoot.width > 0f)
        screenshot("$name-settings")
        clickDescription("关闭设置")
    }

    private fun dismissConfirmation() = dismissDialog("取消")

    private fun dismissDialog(button: String) {
        val dialog = ShadowDialog.getLatestDialog() ?: error("Confirmation missing")
        val dialogRoot = findRoot(dialog.window!!.decorView) ?: error("Dialog Compose root missing")
        val cancel = nodes(dialogRoot.semanticsOwner.rootSemanticsNode).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == button } == true
        }
        assertTrue(cancel.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
        assertFalse(dialog.isShowing)
    }

    private fun show(connected: Boolean, longContent: Boolean = false, transformState: (AppUiState) -> AppUiState = { it }) {
        if (!::controller.isInitialized) {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            activity = controller.get()
        }
        viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        snackbar = SnackbarHostState()
        val fields = listOf(
            ParameterField("prompt", "1", "创作提示词", "CLIPTextEncode", "text", "提示词", "STRING", ParameterKind.MULTILINE, "\"柔和自然光的人像摄影\"", displayValue = "柔和自然光的人像摄影"),
            ParameterField("steps", "2", "采样设置", "KSampler", "steps", "采样步数", "INT", ParameterKind.INTEGER, "24", displayValue = "24", minimum = 1.0, maximum = 100.0),
        ) + if (longContent) (1..24).map { index ->
            ParameterField("extra-$index", "extra-$index", "预览部件 $index", "KSampler", "steps", "采样步数", "INT", ParameterKind.INTEGER, "24", displayValue = "24")
        } else emptyList()
        val workflow = WorkflowDocument(entries[1], "{}", fields, listOf(
            WorkflowNode("1", "创作提示词", "CLIPTextEncode", 0),
            WorkflowNode("2", "采样设置", "KSampler", 1),
            WorkflowNode("3", "保存图像", "SaveImage", 2, isOutput = true),
        ) + if (longContent) (1..24).map { WorkflowNode("extra-$it", "预览部件 $it", "KSampler", it + 2) } else emptyList(), serverUrl = server.baseUrl)
        val sample = File(activity.cacheDir, "preview-image.png")
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(221, 228, 242))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.rgb(149, 166, 208); drawCircle(225f, 95f, 72f, paint)
            paint.color = Color.rgb(86, 111, 169); drawRoundRect(25f, 150f, 270f, 325f, 45f, 45f, paint)
        }
        sample.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        fixtureBitmaps += bitmap
        fixtureLoader?.shutdown()
        fixtureLoader = ImageLoader.Builder(activity).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult = SuccessResult(
                    drawable = BitmapDrawable(activity.resources, bitmap),
                    request = chain.request,
                    dataSource = DataSource.MEMORY_CACHE,
                )
            })
        }.build()
        Coil.setImageLoader(fixtureLoader!!)
        val mediaNames = if (longContent) (0..39).map { "sample-$it.png" } else listOf("sample-a.png", "sample-b.png", "sample-c.png")
        val localMedia = mediaNames.mapIndexed { index, filename ->
            ResultMedia("job-${index / 2}", "3", filename = filename, subfolder = "", type = "output", kind = MediaKind.IMAGE, url = sample.toURI().toString(), localPath = sample.absolutePath, workflowName = "人像精修", createdAt = 1_793_400_000_000L + index)
        }
        val state = transformState(AppUiState(
            serverInput = server.baseUrl, savedServers = listOf(server, server.copy(id = "remote", name = "远程工作站", baseUrl = "http://100.64.0.10:8188")),
            activeServer = if (connected) server else null,
            status = if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            bridgeReady = connected, workflows = entries + if (longContent) (1..24).map { WorkflowEntry("预览流程 %03d.json".format(it), "workflows/extra-$it.json", false) } else emptyList(),
            systemStats = SystemStats("test-version", "test-frontend", listOf(DeviceStats("预览显卡", 24L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024))),
            previewWorkflow = if (connected) workflow else null,
            selectedWorkflow = if (connected) workflow else null,
            fields = if (connected) fields else emptyList(),
            localResults = localMedia,
            jobs = listOf(
                JobSummary("running-job", JobState.RUNNING, workflowName = "人像精修", submittedByApp = true, progress = 0.45f, currentNode = "采样设置"),
                JobSummary("pending-job", JobState.PENDING, workflowName = "产品摄影"),
                JobSummary("finished-job", JobState.SUCCESS, workflowName = "千问多图", durationMillis = 58000),
            ) + if (longContent) (1..24).map { JobSummary("extra-$it", JobState.SUCCESS, workflowName = "后台任务 $it") } else emptyList(),
        ))
        activity.setContent {
            ComfyMobileTheme {
                var settingsOpen by remember { mutableStateOf(false) }
                Box {
                    if (connected) ConnectedApp(state, viewModel, snackbar) { settingsOpen = true }
                    else ConnectionScreen(state, snackbar, { settings = true }, {}, { connectedAddress = it }, { scans++ }, {})
                    if (settingsOpen) SettingsScreen(state, viewModel, snackbar) { settingsOpen = false }
                }
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
    private fun tagged(value: String): SemanticsNode = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == value }
    private fun text(value: String, clickable: Boolean = false): SemanticsNode = nodes().firstOrNull {
        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == value } == true && (!clickable || it.config.contains(SemanticsActions.OnClick))
    } ?: error("Text missing: $value")
    private fun click(value: String) { assertTrue(text(value, true).config[SemanticsActions.OnClick].action!!.invoke()); settle() }
    private fun isVisible(value: String): Boolean = nodes().any {
        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == value } == true && it.boundsInRoot.height > 1f
    }
    private fun scrollToEnd() {
        repeat(12) {
            val scroll = nodes().firstOrNull { it.config.contains(SemanticsActions.ScrollBy) && it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                ?: error("Vertical scrolling unavailable")
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 650f))
            settle()
        }
    }
    private fun clickDescription(value: String) {
        val node = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
    }

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
