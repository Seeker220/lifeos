package com.lifeos.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifeos.ui.UiPorts
import com.lifeos.ui.screens.chat.ChatScreen
import com.lifeos.ui.screens.goals.GoalsScreen
import com.lifeos.ui.screens.inbox.InboxScreen
import com.lifeos.ui.screens.more.MoreScreen
import com.lifeos.ui.screens.onboarding.OnboardingScreen
import com.lifeos.ui.screens.today.TodayScreen
import com.lifeos.ui.screens.wellbeing.WellbeingScreen
import com.lifeos.ui.shell.LifeOsNavBar
import com.lifeos.ui.theme.LifeOsTheme
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Surface0
import com.lifeos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class LifeOsDestination(val route: String, val label: String, val icon: ImageVector) {
    CHAT("chat", "Chat", Icons.Outlined.AutoAwesome),
    TODAY("today", "Today", Icons.Outlined.CalendarToday),
    GOALS("goals", "Goals", Icons.Outlined.Flag),
    INBOX("inbox", "Inbox", Icons.Outlined.Mail),
    WELLBEING("wellbeing", "Focus", Icons.Outlined.Shield),
    MORE("more", "More", Icons.Outlined.MoreHoriz),
}

@Composable
fun LifeOsApp() {
    if (!UiPorts.isReady) {
        Text("LifeOS starting…", color = TextSecondary)
        return
    }
    val state by UiPorts.value.lifeState.state.collectAsState()
    LifeOsTheme(dynamicColor = state.settings.dynamicColor) {
        if (!state.settings.onboardingComplete) {
            val scope = rememberCoroutineScope()
            OnboardingScreen(
                onDone = {
                    scope.launch {
                        UiPorts.value.lifeState.mutate {
                            it.copy(settings = it.settings.copy(onboardingComplete = true))
                        }
                    }
                },
            )
        } else {
            LifeOsShell()
        }
    }
}

@Composable
private fun LifeOsShell() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        containerColor = Surface0,
        bottomBar = {
            LifeOsNavBar(
                selectedRoute = current,
                onDestinationSelected = { dest -> nav.navigateDestination(dest) },
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalScreenPadding provides padding) {
            NavHost(
                navController = nav,
                startDestination = LifeOsDestination.CHAT.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(Motion.navFade) + slideInVertically { 8 } },
                exitTransition = { fadeOut(Motion.navFade) + slideOutVertically { 8 } },
                popEnterTransition = { fadeIn(Motion.navFade) + slideInVertically { 8 } },
                popExitTransition = { fadeOut(Motion.navFade) + slideOutVertically { 8 } },
            ) {
                composable(LifeOsDestination.CHAT.route) {
                    ChatScreen(onNavigate = { nav.navigateDestination(it) })
                }
                composable(LifeOsDestination.TODAY.route) {
                    TodayScreen(onNavigate = { nav.navigateDestination(it) })
                }
                composable(LifeOsDestination.GOALS.route) {
                    GoalsScreen(onNavigate = { nav.navigateDestination(it) })
                }
                composable(LifeOsDestination.INBOX.route) {
                    InboxScreen(onNavigate = { nav.navigateDestination(it) })
                }
                composable(LifeOsDestination.WELLBEING.route) {
                    WellbeingScreen(onNavigate = { nav.navigateDestination(it) })
                }
                composable(LifeOsDestination.MORE.route) {
                    MoreScreen(onNavigate = { nav.navigateDestination(it) })
                }
            }
        }
    }
}

private fun NavHostController.navigateDestination(dest: LifeOsDestination) {
    val startDestination = graph.findStartDestination().id
    navigate(dest.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(startDestination) { saveState = true }
    }
}
