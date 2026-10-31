package com.local.comfyuimobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
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
class OnboardingScreenTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var root: ViewRootForTest
    private var finished = false

    @After fun tearDown() {
        if (::controller.isInitialized) {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun walkthroughExplainsConnectionEditingAndLocalSavingBeforeFinish() {
        show()
        assertTrue(hasText("把工作站，带到手边。"))
        screenshot("guide-phone")
        click("下一步")
        assertTrue(hasText("熟悉的工作流，更顺手。"))
        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        assertTrue(hasText("把工作站，带到手边。"))
        click("下一步")
        click("下一步")
        assertTrue(hasText("设置自动保存规则"))
        screenshot("guide-saving-phone")
        click("下一步")
        assertTrue(hasText("更新与反馈在哪里？"))
        assertFalse(finished)
        click("开始使用")
        assertTrue(finished)
    }

    @Test fun skipAndSystemBackAllowLeavingWithoutConnecting() {
        show()
        click("暂时跳过")
        assertTrue(finished)
        finished = false
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(finished)
    }

    @Test fun connectionInstructionsCanBeScrolledToStartupMethodsAndCloudLimits() {
        show()
        val scroll = nodes().first { it.config.contains(SemanticsActions.ScrollBy) }
        repeat(4) {
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 300f))
            settle()
        }
        click("便携版")
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("不要加到 pause 行") } == true })
        click("手动启动")
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("python main.py --listen 0.0.0.0 --port 8188") } == true })
        assertTrue(hasText("哪些连接可以用？"))
        click("查看 Windows 10 / 11 放行步骤")
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("特定本地端口") && text.text.contains("8188") } == true })
        click("收起防火墙步骤")
        screenshot("guide-connection-details")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowPhoneWithLargeTextKeepsNavigationVisibleAndContentScrollable() {
        RuntimeEnvironment.setFontScale(1.3f)
        show()
        val next = node("下一步")
        assertTrue(next.boundsInRoot.bottom <= activity.window.decorView.height)
        assertTrue(nodes().any { it.config.contains(SemanticsActions.ScrollBy) })
        screenshot("guide-small-large-text")
        click("下一步")
        assertTrue(hasText("调整参数，再生成"))
    }

    @Test @Config(qualifiers = "w1000dp-h720dp-night-mdpi")
    fun tabletAllowsJumpingBetweenStepsInDarkTheme() {
        show()
        click("3  保存作品")
        assertTrue(hasText("让好作品，留在手机里。"))
        screenshot("guide-tablet-dark")
        click("1  连接工作站")
        assertTrue(hasText("填入工作站地址"))
    }

    private fun show() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
        activity.setContent { ComfyMobileTheme { OnboardingScreen { finished = true } } }
        settle()
        root = findRoot(activity.window.decorView) ?: error("Missing Compose root")
    }

    private fun settle() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val decor = activity.window.decorView
            val config = activity.resources.configuration
            val density = activity.resources.displayMetrics.density
            decor.measure(View.MeasureSpec.makeMeasureSpec((config.screenWidthDp * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((config.screenHeightDp * density).toInt(), View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
            findRoot(decor)?.measureAndLayoutForTest()
        }
    }

    private fun findRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findRoot(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun nodes(node: SemanticsNode = root.semanticsOwner.rootSemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { nodes(it) }
    private fun node(text: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true }
    private fun hasText(text: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true }
    private fun click(text: String) {
        assertTrue(node(text).config[SemanticsActions.OnClick].action!!.invoke())
        settle()
    }
    private fun screenshot(name: String) {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/outputs/onboarding-preview/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
