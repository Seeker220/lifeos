package com.lifeos.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifeos.core.Personas
import com.lifeos.ui.UiPorts
import com.lifeos.ui.screens.chat.ChatScreen
import com.lifeos.ui.screens.goals.GoalsScreen
import com.lifeos.ui.screens.inbox.InboxScreen
import com.lifeos.ui.screens.more.MoreScreen
import com.lifeos.ui.screens.onboarding.OnboardingScreen
import com.lifeos.ui.screens.today.TodayScreen
import com.lifeos.ui.screens.wellbeing.WellbeingScreen
import com.lifeos.ui.theme.MdBg
import com.lifeos.ui.theme.MdOnSurface
import com.lifeos.ui.theme.MdPrimary
import kotlinx.coroutines.launch

enum class LifeOsDestination(val route: String, val label: String, val icon: ImageVector) {
    CHAT("chat", "Chat", Icons.Outlined.AutoAwesome),
    TODAY("today", "Today", Icons.Outlined.CalendarToday),
    GOALS("goals", "Goals", Icons.Outlined.Flag),
    INBOX("inbox", "Inbox", Icons.Outlined.Mail),
    WELLBEING("wellbeing", "Focus", Icons.Outlined.Shield),
    MORE("more", "More", Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeOsApp() {
    if (!UiPorts.isReady) {
        Text("LifeOS starting…")
        return
    }
    val state by UiPorts.value.lifeState.state.collectAsState()
    val scope = rememberCoroutineScope()
    if (!state.settings.onboardingComplete) {
        OnboardingScreen(onDone = {
            scope.launch {
                UiPorts.value.lifeState.mutate {
                    it.copy(settings = it.settings.copy(onboardingComplete = true))
                }
            }
        })
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val persona = remember(state.personaId) { Personas.byId(state.personaId) }
    Scaffold(
        containerColor = MdBg,
        topBar = {
            TopAppBar(
                title = { Text("LifeOS") },
                actions = { Text(persona.name, color = MdPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MdBg,
                    titleContentColor = MdOnSurface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MdBg) {
                LifeOsDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = { nav.navigate(dest.route) { launchSingleTop = true } },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = LifeOsDestination.CHAT.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(LifeOsDestination.CHAT.route) {
                ChatScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
            composable(LifeOsDestination.TODAY.route) {
                TodayScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
            composable(LifeOsDestination.GOALS.route) {
                GoalsScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
            composable(LifeOsDestination.INBOX.route) {
                InboxScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
            composable(LifeOsDestination.WELLBEING.route) {
                WellbeingScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
            composable(LifeOsDestination.MORE.route) {
                MoreScreen(onNavigate = { nav.navigate(it.route) { launchSingleTop = true } })
            }
        }
    }
}
