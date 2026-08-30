package com.lifeos.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lifeos.core.Persona
import com.lifeos.core.Personas
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.pressable
import com.lifeos.ui.theme.AccentWash
import com.lifeos.ui.theme.Backdrop
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.surface
import kotlinx.coroutines.launch

@Composable
fun PersonaPill(
    persona: Persona,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .pressable {
                onClick()
                sheetOpen = true
            }
            .clip(RoundedCornerShape(Radius.full))
            .background(AccentWash)
            .padding(horizontal = S.x3, vertical = S.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = persona.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
        )
    }
    if (sheetOpen) {
        PersonaSheet(
            selectedId = persona.id,
            onDismiss = { sheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaSheet(
    selectedId: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        scrimColor = Backdrop.copy(alpha = 0.88f),
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Text(
            text = "Persona",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        )
        Personas.ALL.forEach { option ->
            val selected = option.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S.x4, vertical = S.x2)
                    .pressable {
                        if (!selected && UiPorts.isReady) {
                            scope.launch {
                                UiPorts.value.executor.execute(
                                    listOf(Action.SetPersona(option.id)),
                                    ActionOrigin.USER,
                                )
                            }
                        }
                        onDismiss()
                    }
                    .surface(level = if (selected) 3 else 1, radius = Radius.lg, active = selected)
                    .padding(S.x4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(S.x3),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else TextSecondary,
                        ),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = option.voice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(S.x4))
    }
}
