package com.conndreams.recorder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.conndreams.recorder.ui.theme.Border1
import com.conndreams.recorder.ui.theme.GoldLight
import com.conndreams.recorder.ui.theme.Surface2

data class SettingsState(
    val accountEmail: String?,
    val recordOnLaunch: Boolean,
    val beepEnabled: Boolean,
    val hapticEnabled: Boolean,
    val maxLengthMinutes: Int,
)

data class SettingsCallbacks(
    val onClose: () -> Unit,
    val onSwitchAccount: () -> Unit,
    val onRecordOnLaunchChange: (Boolean) -> Unit,
    val onBeepChange: (Boolean) -> Unit,
    val onHapticChange: (Boolean) -> Unit,
    val onMaxLengthChange: (Int) -> Unit,
)

@Composable
fun SettingsSheet(state: SettingsState, callbacks: SettingsCallbacks) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AlchemyBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 40.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontStyle = FontStyle.Italic),
                        color = GoldLight,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = callbacks.onClose) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (state.accountEmail != null) {
                    SectionTitle("Drive account")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = state.accountEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = callbacks.onSwitchAccount,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    text = "Switch Google account",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                SectionTitle("Behaviour")
                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    title = "Record when app opens",
                    subtitle = "Tapping the icon or Side Key starts a recording immediately. Long-press the icon → Settings to bypass.",
                    checked = state.recordOnLaunch,
                    onCheckedChange = callbacks.onRecordOnLaunchChange,
                )

                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    title = "Beep on start/stop",
                    checked = state.beepEnabled,
                    onCheckedChange = callbacks.onBeepChange,
                )

                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    title = "Haptic on start/stop",
                    checked = state.hapticEnabled,
                    onCheckedChange = callbacks.onHapticChange,
                )

                Spacer(Modifier.height(24.dp))

                SectionTitle("Maximum recording length")
                Spacer(Modifier.height(8.dp))

                MaxLengthGroup(
                    selected = state.maxLengthMinutes,
                    onSelect = callbacks.onMaxLengthChange,
                )

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = Surface2,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun MaxLengthGroup(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            options.forEachIndexed { i, (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = value == selected, onClick = { onSelect(value) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (i < options.lastIndex) {
                    HorizontalDivider(color = Border1, thickness = 0.5.dp)
                }
            }
        }
    }
}
