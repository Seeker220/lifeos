package com.lifeos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.lifeos.ui.nav.LifeOsApp
import com.lifeos.ui.theme.LifeOsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDemo(intent)
        setContent {
            LifeOsTheme {
                LifeOsApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDemo(intent)
    }

    private fun handleDemo(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val ports = (application as LifeOsApplication).container
        intent?.getStringExtra("say")?.let { text ->
            intent.removeExtra("say")
            lifecycleScope.launch { ports.agent.send(text) }
        }
        when (intent?.getStringExtra("demo")) {
            "seed" -> lifecycleScope.launch { DemoSeed.seed(ports) }
            "fill_chat" -> lifecycleScope.launch { DemoSeed.fillChat(ports) }
        }
        intent?.removeExtra("demo")
    }
}
