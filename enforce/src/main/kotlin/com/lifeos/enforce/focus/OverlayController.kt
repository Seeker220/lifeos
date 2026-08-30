package com.lifeos.enforce.focus

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.lifeos.core.LifeOsLog
import com.lifeos.enforce.R

enum class BlockReason { FOCUS, TIMEOUT }

class OverlayController(
    context: Context,
    private val onOverride: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val inflaterContext = ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault)

    private var view: View? = null
    private var shownReason: BlockReason? = null
    private var shownTitle: String? = null
    private var blockedPackage: String = ""

    @Volatile
    private var showing: Boolean = false

    val isShowing: Boolean get() = showing

    fun show(reason: BlockReason, title: String, subtitle: String, sourceLabel: String?) {
        show(reason, title, subtitle, sourceLabel, blockedPackage)
    }

    fun show(
        reason: BlockReason,
        title: String,
        subtitle: String,
        sourceLabel: String?,
        packageName: String,
    ) {
        postToMain {
            showOnMain(reason, title, subtitle, sourceLabel, packageName)
        }
    }

    fun hide() {
        postToMain { hideOnMain() }
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }

    private fun showOnMain(
        reason: BlockReason,
        title: String,
        subtitle: String,
        sourceLabel: String?,
        packageName: String,
    ) {
        blockedPackage = packageName
        val existing = view
        if (existing != null && existing.parent != null && shownReason == reason && shownTitle == title) {
            bind(existing, title, subtitle, sourceLabel)
            showing = true
            return
        }
        val target = existing ?: inflate()
        view = target
        bind(target, title, subtitle, sourceLabel)
        if (target.parent == null) {
            val added = runCatching {
                windowManager.addView(target, windowParams())
            }.onFailure {
                LifeOsLog.d("LifeOS/Focus", "overlay addView failed: ${it.message}")
            }.isSuccess
            if (!added) {
                showing = false
                return
            }
        }
        shownReason = reason
        shownTitle = title
        showing = true
    }

    private fun hideOnMain() {
        val current = view
        if (current != null) {
            runCatching {
                if (current.parent != null) windowManager.removeView(current)
            }.onFailure {
                LifeOsLog.d("LifeOS/Focus", "overlay removeView failed: ${it.message}")
            }
        }
        view = null
        shownReason = null
        shownTitle = null
        blockedPackage = ""
        showing = false
    }

    private fun inflate(): View {
        val root = LayoutInflater.from(inflaterContext).inflate(R.layout.overlay_block, null)
        root.findViewById<Button>(R.id.overlay_back).setOnClickListener {
            hideOnMain()
            goHome()
        }
        root.findViewById<Button>(R.id.overlay_override).setOnClickListener {
            val pkg = blockedPackage
            hideOnMain()
            if (pkg.isNotBlank()) onOverride(pkg)
        }
        return root
    }

    private fun bind(root: View, title: String, subtitle: String, sourceLabel: String?) {
        root.findViewById<TextView>(R.id.overlay_title).text = title
        root.findViewById<TextView>(R.id.overlay_subtitle).text = subtitle
        val source = root.findViewById<TextView>(R.id.overlay_source)
        if (sourceLabel.isNullOrBlank()) {
            source.visibility = View.GONE
            source.text = ""
        } else {
            source.visibility = View.VISIBLE
            source.text = sourceLabel
        }
    }

    private fun goHome() {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(home)
        }.onFailure {
            LifeOsLog.d("LifeOS/Focus", "HOME intent failed: ${it.message}")
        }
    }

    private fun windowParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
}
