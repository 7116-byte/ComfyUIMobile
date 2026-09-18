package com.local.comfyuimobile.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** A non-floating window measured against the display, not the parent Scaffold. */
@Composable
internal fun FullscreenGalleryDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val composition = rememberCompositionContext()
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    val dialog = remember(context) {
        object : ComponentDialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                super.onWindowFocusChanged(hasFocus)
                if (hasFocus) window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView)
                        .hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
    DisposableEffect(dialog, composition) {
        val contentView = ComposeView(dialog.context).apply {
            setParentCompositionContext(composition)
            setContent { currentContent() }
        }
        dialog.setContentView(contentView)
        dialog.setOnDismissListener { currentDismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setWindowAnimations(0)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            if (Build.VERSION.SDK_INT >= 28) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(this, decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            contentView.disposeComposition()
        }
    }
}
