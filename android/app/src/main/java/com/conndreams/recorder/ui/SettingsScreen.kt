package com.conndreams.recorder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.conndreams.recorder.ui.theme.AmberYellow
import com.conndreams.recorder.ui.theme.Border1
import com.conndreams.recorder.ui.theme.DeepViolet
import com.conndreams.recorder.ui.theme.EmberOrange
import com.conndreams.recorder.ui.theme.Gold
import com.conndreams.recorder.ui.theme.GoldLight
import com.conndreams.recorder.ui.theme.MossGreen
import com.conndreams.recorder.ui.theme.Surface2

data class SettingsState(
    val accountEmail: String?,
    val folderName: String,
    val pendingCount: Int,
    val damagedCount: Int,
    val isRecording: Boolean,
    val recordOnLaunch: Boolean,
    val beepEnabled: Boolean,
    val hapticEnabled: Boolean,
    val maxLengthMinutes: Int,
)

data class SettingsCallbacks(
    val onConnect: () -> Unit,
    val onTestRecord: () -> Unit,
    val onCleanupDamaged: () -> Unit,
    val onRecordOnLaunchChange: (Boolean) -> Unit,
    val onBeepChange: (Boolean) -> Unit,
    val onHapticChange: (Boolean) -> Unit,
    val onMaxLengthChange: (Int) -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsState, callbacks: SettingsCallbacks) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            HeaderBlock()

            Spacer(Modifier.height(28.dp))

            DriveStatusCard(
                accountEmail = state.accountEmail,
                folderName = state.folderName,
                onConnect = callbacks.onConnect,
            )

            if (state.pendingCount > 0 || state.damagedCount > 0) {
                Spacer(Modifier.height(12.dp))
                StatusChips(
                    pending = state.pendingCount,
                    damaged = state.damagedCount,
                    onCleanupDamaged = callbacks.onCleanupDamaged,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = callbacks.onTestRecord,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) EmberOrange else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Icon(
                    imageVector = if (state.isRecording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state.isRecording) "Stop recording" else "Record a test dream",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(36.dp))
            DividerWithGlyph()
            Spacer(Modifier.height(28.dp))

            SectionTitle("Behaviour")

            Spacer(Modifier.height(12.dp))

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

            Spacer(Modifier.height(28.dp))

            SectionTitle("Maximum recording length")

            Spacer(Modifier.height(8.dp))

            MaxLengthGroup(
                selected = state.maxLengthMinutes,
                onSelect = callbacks.onMaxLengthChange,
            )

            Spacer(Modifier.height(36.dp))

            SideKeyHint()

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column {
        Text(
            text = "Conn Dreams",
            style = MaterialTheme.typography.headlineLarge.copy(fontStyle = FontStyle.Italic),
            color = GoldLight,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "an alchemical record of the night",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun DriveStatusCard(
    accountEmail: String?,
    folderName: String,
    onConnect: () -> Unit,
) {
    val connected = accountEmail != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = if (connected) MossGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (connected) "Connected to Drive" else "Not connected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (connected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = accountEmail!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Folder: $folderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = if (connected) "Switch Google account" else "Connect Google Drive",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StatusChips(pending: Int, damaged: Int, onCleanupDamaged: () -> Unit) {
    Column {
        if (pending > 0) {
            Text(
                text = "$pending recording(s) waiting to upload",
                style = MaterialTheme.typography.bodySmall,
                color = AmberYellow,
            )
        }
        if (damaged > 0) {
            if (pending > 0) Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.clickable(onClick = onCleanupDamaged),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = EmberOrange,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$damaged damaged recording(s) — tap to clean up",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmberOrange,
                )
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
        color = MaterialTheme.colorScheme.surface,
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
        color = MaterialTheme.colorScheme.surface,
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

@Composable
private fun SideKeyHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DeepViolet.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tip — Side Key",
                style = MaterialTheme.typography.titleMedium,
                color = GoldLight,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Settings → Advanced features → Side key → Double press → Open app → Conn Dreams. Double-press from the lock screen to start a recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DividerWithGlyph() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.6f)),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}
