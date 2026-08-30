package com.lifeos.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.AccentWash
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.theme.TextTertiary

@Composable
fun LifeOsNavBar(
    selectedRoute: String?,
    onDestinationSelected: (LifeOsDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<LifeOsDestination> = LifeOsDestination.entries,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface2.copy(alpha = 0.92f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 56.dp)
                .padding(horizontal = S.x1, vertical = S.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEach { dest ->
                val selected = selectedRoute == dest.route
                NavBarItem(
                    dest = dest,
                    selected = selected,
                    onClick = { onDestinationSelected(dest) },
                    modifier = Modifier.weight(if (selected) 1.7f else 1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    dest: LifeOsDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val pill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.emphasized,
        label = "navPill",
    )
    val sizeSpec = spring<IntSize>(dampingRatio = 0.80f, stiffness = 380f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .pressable(onClick)
                .clip(RoundedCornerShape(Radius.full))
                .background(AccentWash.copy(alpha = AccentWash.alpha * pill))
                .animateContentSize(animationSpec = sizeSpec)
                .padding(horizontal = S.x3, vertical = S.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S.x1),
        ) {
            Icon(
                imageVector = dest.icon,
                contentDescription = dest.label,
                tint = if (selected) primary else TextTertiary,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(Motion.navFade) + expandHorizontally(animationSpec = sizeSpec),
                exit = fadeOut(Motion.navFade) + shrinkHorizontally(animationSpec = sizeSpec),
            ) {
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
