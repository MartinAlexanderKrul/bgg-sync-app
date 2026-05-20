package cz.nicolsburg.boardflow.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers

data class SectionStat(
    val label: String,
    val value: String,
    val icon: ImageVector? = null
)

data class HeaderChip(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
fun InlineStat(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    large: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(if (large) 18.dp else 14.dp),
            tint = tint
        )
        Text(
            text = label,
            style = if (large) {
                MaterialTheme.typography.titleMedium.withTabularNumbers()
            } else {
                MaterialTheme.typography.labelMedium.withTabularNumbers()
            },
            fontWeight = if (large) FontWeight.SemiBold else null,
            color = tint
        )
    }
}

fun playerLabel(game: GameItem): String? {
    return when {
        game.minPlayers != null && game.maxPlayers != null && game.minPlayers != game.maxPlayers ->
            "${game.minPlayers}-${game.maxPlayers} players"

        game.minPlayers != null ->
            "${game.minPlayers} players"

        else -> null
    }
}

fun formatDecimal(value: Double): String = String.format("%.1f", value)

fun gameWeightLabel(weight: Double): String = when {
    weight < 1.5 -> "Light"
    weight < 2.0 -> "Casual"
    weight < 2.5 -> "Medium-Light"
    weight < 3.0 -> "Medium"
    weight < 3.5 -> "Medium-Heavy"
    weight < 4.0 -> "Heavy"
    else         -> "Expert"
}

fun overviewStats(game: GameItem): List<SectionStat> {
    val stats = mutableListOf<SectionStat>()
    game.weight?.let { stats += SectionStat("Difficulty", gameWeightLabel(it)) }
    game.yearPublished?.toString()?.let { stats += SectionStat("Year", it) }
    compactPlayTime(game)?.let { stats += SectionStat("Play time", it) }
    game.recommendedAge?.takeIf { it.isNotBlank() }?.let { stats += SectionStat("Age", it) }
    return stats
}

fun ratingStats(game: GameItem): List<SectionStat> {
    return listOfNotNull(
        game.rating?.let { SectionStat("BGG rating", formatDecimal(it)) },
        game.bayesAverage?.let { SectionStat("Bayes rating", formatDecimal(it)) },
    )
}

fun playerPreferenceStats(game: GameItem): List<SectionStat> {
    return listOfNotNull(
        game.bestPlayers?.takeIf { it.isNotBlank() }?.let { SectionStat("Best for", it) },
        game.recommendedPlayers?.takeIf { it.isNotBlank() }?.let { SectionStat("Recommended for", it) }
    )
}

fun customDetailRows(game: GameItem): List<SectionStat> {
    val handledKeys = setOf(
        "objectid",
        "collid",
        "objectname",
        "game",
        "objecttype",
        "originalname",
        "yearpublished",
        "year",
        "rank",
        "average",
        "score",
        "communityrating",
        "baverage",
        "avgweight",
        "weight",
        "minplayers",
        "maxplayers",
        "playingtime",
        "minplaytime",
        "maxplaytime",
        "numowned",
        "numplays",
        "thumbnail",
        "shareurl",
        "share_url",
        "share url",
        "qrimage",
        "qr_image",
        "qr image",
        "drive",
        "language",
        "languagedependence",
        "bgglanguagedependence",
        "bggbestplayers",
        "bggrecplayers",
        "bggrecagerange",
        "bggurl",
        "own",
        "wishlist",
        "numowned",
        "price",
        "origprice",
        "orig price",
        "sleeved",
        "sleeves",
        "sleevejson"
    )

    return game.spreadsheetValues.entries
        .filter { (key, value) -> value.isNotBlank() && key !in handledKeys }
        .sortedBy { it.key }
        .map { (key, value) -> SectionStat(formatSourceKey(key), value) }
}

fun bggSleevesUrl(game: GameItem): String? {
    val baseUrl = game.bggUrl?.substringBefore('?')?.trimEnd('/')
    if (!baseUrl.isNullOrBlank()) {
        return if (baseUrl.endsWith("/sleeves", ignoreCase = true)) baseUrl else "$baseUrl/sleeves"
    }

    val objectId = game.objectId.takeIf { it.isNotBlank() } ?: return null
    val objectType = game.spreadsheetValues["objecttype"] ?: game.bggValues["objecttype"]

    val route = when (objectType?.trim()?.lowercase()) {
        "boardgameexpansion" -> "boardgameexpansion"
        "boardgameaccessory" -> "boardgameaccessory"
        else -> "boardgame"
    }

    return "https://boardgamegeek.com/$route/$objectId/sleeves"
}

fun formatSourceKey(key: String): String {
    if (key.equals("origprice", ignoreCase = true) || key.equals("orig price", ignoreCase = true)) {
        return "Price"
    }

    return key
        .replace('_', ' ')
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { it.titlecase() }
        }
}

fun compactPlayTime(game: GameItem): String? {
    val playTime = game.playingTime
    val minPlayTime = game.minPlayTime
    val maxPlayTime = game.maxPlayTime

    return when {
        playTime != null -> "${playTime} min"
        minPlayTime != null && maxPlayTime != null && minPlayTime != maxPlayTime -> "$minPlayTime-$maxPlayTime min"
        minPlayTime != null -> "${minPlayTime} min"
        maxPlayTime != null -> "${maxPlayTime} min"
        else -> null
    }
}

internal fun sheetSleeveStatus(game: GameItem): SleeveTrackingState {
    val value = game.spreadsheetValues.entries
        .firstOrNull { (key, _) -> key.equals("sleeved", ignoreCase = true) }
        ?.value
        ?.trim()

    return SleeveTrackingState.fromSheetValue(value)
}

fun isSleeved(game: GameItem): Boolean =
    sheetSleeveStatus(game) == SleeveTrackingState.SLEEVED

internal fun sleeveTrackingLabel(status: SleeveTrackingState): String = when (status) {
    SleeveTrackingState.SLEEVED -> "Sleeved"
    SleeveTrackingState.TO_SLEEVE -> "To sleeve"
    SleeveTrackingState.POSSIBLE -> "Possible to sleeve"
    SleeveTrackingState.NOT_SLEEVING -> "Not sleeving"
    SleeveTrackingState.UNKNOWN -> "Not tracked"
}

fun headerStatusChips(
    game: GameItem,
    primary: Color,
    secondary: Color
): List<HeaderChip> {
    return buildList {
        when (sheetSleeveStatus(game)) {
            SleeveTrackingState.SLEEVED -> add(HeaderChip("Sleeved", Icons.Default.Check, primary))
            SleeveTrackingState.TO_SLEEVE -> add(HeaderChip("To sleeve", Icons.Default.WarningAmber, secondary))
            SleeveTrackingState.POSSIBLE -> add(HeaderChip("Possible", Icons.Default.Style, secondary))
            SleeveTrackingState.NOT_SLEEVING,
            SleeveTrackingState.UNKNOWN -> Unit
        }
        if (game.isOwned) {
            add(HeaderChip("Owned", Icons.Default.Inventory2, primary))
        }
        if (game.isWishlisted) {
            add(HeaderChip("Wishlist", Icons.Default.Bookmark, secondary))
        }
    }
}

// ── Category icon mappings ────────────────────────────────────────────────

fun gameWeightIcon(weight: Double): ImageVector = when {
    weight < 1.5 -> Icons.Default.Spa          // Light – beginner friendly
    weight < 2.0 -> Icons.Default.LocalCafe    // Casual – learning strategy
    weight < 2.5 -> Icons.Default.Build        // Medium-Light – building tactical skills
    weight < 3.0 -> Icons.Default.Psychology   // Medium – proper strategic gaming
    weight < 3.5 -> Icons.Default.Shield       // Medium-Heavy – experienced players
    weight < 4.0 -> Icons.Default.Whatshot     // Heavy – hardcore strategy
    else         -> Icons.Default.EmojiEvents  // Expert – legendary brain burner
}

fun playTimeIcon(minutes: Int): ImageVector = when {
    minutes < 20  -> Icons.Default.Bolt             // Lightning quick
    minutes < 45  -> Icons.Default.Timer             // Short
    minutes < 90  -> Icons.Default.Schedule          // Medium
    minutes < 180 -> Icons.Default.HourglassBottom   // Long
    else          -> Icons.Default.HourglassFull     // Marathon
}

fun gamePlayTimeIcon(game: GameItem): ImageVector? {
    val minutes = game.playingTime ?: game.minPlayTime ?: game.maxPlayTime ?: return null
    return playTimeIcon(minutes)
}

fun ratingIcon(rating: Double): ImageVector = when {
    rating < 5.5 -> Icons.Default.SentimentNeutral   // Below average
    rating < 6.5 -> Icons.Default.SentimentSatisfied // Decent
    rating < 7.5 -> Icons.Default.Star               // Good
    rating < 8.5 -> Icons.Default.Grade              // Great
    else         -> Icons.Default.EmojiEvents         // Exceptional
}
