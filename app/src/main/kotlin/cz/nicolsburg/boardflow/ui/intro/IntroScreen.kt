package cz.nicolsburg.boardflow.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton

@Composable
fun IntroScreen(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            item { IntroHeader() }
            item { IntroFeatures() }
            item { IntroSetupSteps() }
            item { IntroSettingsOverview() }
            item {
                BoardFlowButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it, let's play!")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SetupGuideContent() {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        IntroHeader()
        IntroFeatures()
        IntroSetupSteps()
        IntroSettingsOverview()
    }
}

@Composable
private fun IntroHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Welcome to BoardFlow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Your board game play tracker — from the table to the stats.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IntroFeatures() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "What you can do",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        FeatureRow(Icons.Default.NoteAdd, "Log plays", "Record scores, players, moods, and locations for any game.")
        FeatureRow(Icons.Default.CameraAlt, "Scan scoresheets", "Use your camera and AI to extract scores automatically.")
        FeatureRow(Icons.AutoMirrored.Filled.MenuBook, "Play journal & stats", "Browse history, filter by game or player, and track wins.")
        FeatureRow(Icons.Default.GridView, "Collection browser", "View your owned games, wishlists, and sleeve recommendations.")
        FeatureRow(Icons.Default.EmojiEvents, "Challenges", "Set personal play goals and track progress.")
        FeatureRow(Icons.Default.GridOn, "Google Sheets export", "Optionally sync plays to a spreadsheet for custom analysis.")
    }
}

@Composable
private fun IntroSetupSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Getting started",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        SetupStep(
            number = 1,
            title = "Add your BGG account",
            description = "Go to Settings → Accounts and enter your BoardGameGeek username and password. This lets the app read your collection.",
            optional = false
        )
        SetupStep(
            number = 2,
            title = "Sync your collection",
            description = "Open the Sync tab and tap Refresh to pull in your owned games and wishlists from BGG.",
            optional = false
        )
        SetupStep(
            number = 3,
            title = "Connect Google Sheets",
            description = "In Settings → Accounts, sign in with Google and link a spreadsheet to export plays automatically.",
            optional = true
        )
        SetupStep(
            number = 4,
            title = "Enable AI scoresheet scanning",
            description = "Add a Gemini API key in Settings → Scan to let the camera read scoresheet photos and fill in scores for you.",
            optional = true
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, description: String, optional: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .background(
                    color = if (optional) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = if (optional) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (optional) {
                    Text(
                        "optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntroSettingsOverview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Settings reference",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "All settings are in the Settings tab at the bottom of the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsGroupHeader("Accounts")
        SettingItem(
            icon = Icons.Default.People,
            title = "BoardGameGeek",
            description = "Your BGG username and password. Required to sync your collection and play history from BGG."
        )
        SettingItem(
            icon = Icons.Default.CloudDone,
            title = "Google",
            description = "Sign in with Google to enable Google Sheets export. Completely optional — the rest of the app works without it.",
            optional = true
        )

        SettingsGroupHeader("Preferences")
        SettingItem(
            icon = Icons.Default.Palette,
            title = "Theme",
            description = "Switch between Light, Dark, or follow the system setting."
        )
        SettingItem(
            icon = Icons.Default.GridOn,
            title = "History stats",
            description = "Choose which plays count toward stats — all logged plays, locally logged only, or BGG-synced plays."
        )
        SettingItem(
            icon = Icons.Default.People,
            title = "Recommendations",
            description = "Shows \"Try next\" game suggestions after logging a play. Can be turned off if you prefer a cleaner log screen.",
            canDisable = true
        )
        SettingItem(
            icon = Icons.Default.AutoStories,
            title = "Chronicles",
            description = "Auto-generates a short memory line for each session (requires a Gemini key). Turn off to skip AI-generated summaries.",
            canDisable = true
        )
        SettingItem(
            icon = Icons.Default.Layers,
            title = "Sleeve manufacturer",
            description = "Sets your preferred brand for sleeve size recommendations shown in the Collection tab."
        )
        SettingItem(
            icon = Icons.Default.Bookmark,
            title = "Mood templates",
            description = "Add custom mood labels beyond the built-in presets, available when logging a play session."
        )

        SettingsGroupHeader("Scan")
        SettingItem(
            icon = Icons.Default.AutoAwesome,
            title = "Gemini API key",
            description = "Your Google AI Studio key. Required for scoresheet scanning and chronicle generation. Free tier is sufficient for casual use.",
            optional = true
        )
        SettingItem(
            icon = Icons.Default.AutoAwesome,
            title = "Gemini model",
            description = "Which AI model to use for scanning. Use \"Refresh available models\" to see what your key has access to.",
            optional = true
        )
        SettingItem(
            icon = Icons.Default.CameraAlt,
            title = "Recognition templates",
            description = "Saved scoresheet layouts learned from previous scans. More templates = better accuracy over time. Can be cleared to reset."
        )
        SettingItem(
            icon = Icons.Default.Person,
            title = "Player recognition hints",
            description = "Mappings from scan output names to your roster players, learned automatically. Clear these to reset auto-fill behaviour."
        )

        SettingsGroupHeader("Data")
        SettingItem(
            icon = Icons.Default.Storage,
            title = "Collection cache",
            description = "Locally cached BGG collection. Clear it to force a full re-sync on the next Sync tab refresh."
        )
        SettingItem(
            icon = Icons.Default.Backup,
            title = "Backup & Restore",
            description = "Export your full app state (plays, roster, challenges, settings) to a file. Use Restore on a new device to move everything over."
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    optional: Boolean = false,
    canDisable: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (optional) {
                    SettingBadge("optional")
                }
                if (canDisable) {
                    SettingBadge("can disable")
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    )
}
