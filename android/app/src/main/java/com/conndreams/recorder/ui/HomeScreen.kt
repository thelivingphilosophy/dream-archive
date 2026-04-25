package com.conndreams.recorder.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.conndreams.recorder.R
import com.conndreams.recorder.ui.theme.AmberYellow
import com.conndreams.recorder.ui.theme.DeepViolet
import com.conndreams.recorder.ui.theme.EmberOrange
import com.conndreams.recorder.ui.theme.Gold
import com.conndreams.recorder.ui.theme.GoldLight
import com.conndreams.recorder.ui.theme.MossGreen

@Composable
fun HomeScreen(
    state: HomeState,
    callbacks: HomeCallbacks,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AlchemyBackground {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 56.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeroBlock()

                    Spacer(Modifier.height(36.dp))

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

                    Spacer(Modifier.height(24.dp))

                    RecordButton(
                        isRecording = state.isRecording,
                        onClick = callbacks.onTestRecord,
                    )

                    if (state.accountEmail != null && state.driveFolderId != null) {
                        Spacer(Modifier.height(10.dp))
                        OpenFolderButton(onClick = callbacks.onOpenDriveFolder)
                    }

                    Spacer(Modifier.weight(1f, fill = true))
                    Spacer(Modifier.height(28.dp))

                    DividerWithGlyph()

                    Spacer(Modifier.height(20.dp))

                    SideKeyHint()
                }

                IconButton(
                    onClick = callbacks.onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 28.dp, end = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

data class HomeState(
    val accountEmail: String?,
    val folderName: String,
    val driveFolderId: String?,
    val pendingCount: Int,
    val damagedCount: Int,
    val isRecording: Boolean,
)

data class HomeCallbacks(
    val onConnect: () -> Unit,
    val onTestRecord: () -> Unit,
    val onOpenDriveFolder: () -> Unit,
    val onCleanupDamaged: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun HeroBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The DREAM Archive",
            style = MaterialTheme.typography.headlineLarge.copy(fontStyle = FontStyle.Italic),
            color = GoldLight,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "an alchemical record of the night",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = "Connect Google Drive",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChips(pending: Int, damaged: Int, onCleanupDamaged: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-pulse",
    )

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) EmberOrange else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(vertical = 18.dp),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .scale(if (isRecording) pulse else 1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isRecording) "Stop recording" else "Record a dream",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun OpenFolderButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Open Drive folder",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SideKeyHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DeepViolet.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "☿",
                    style = MaterialTheme.typography.titleLarge,
                    color = GoldLight.copy(alpha = 0.85f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tip — Side Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldLight,
                    fontStyle = FontStyle.Italic,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Settings → Advanced features → Side key → Double press → Open app → The DREAM Archive. Double-press from the lock screen to start a recording.",
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
