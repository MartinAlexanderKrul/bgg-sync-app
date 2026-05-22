package cz.nicolsburg.boardflow.data.chronicle

import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.SessionMemory
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Locale

data class ChroniclePlan(
    val memory: SessionMemory,
    val sourceKey: String?,
    val needsGeneration: Boolean
)

class SessionChronicleService(
    private val lineGenerator: ChronicleLineGenerator,
    private val fallbackComposer: FallbackChronicleComposer
) {

    fun plan(play: LoggedPlay, draft: SessionMemory, existing: SessionMemory?): ChroniclePlan {
        val normalized = draft.copy(
            moods = draft.moods.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            momentType = draft.momentType.trim(),
            note = draft.note.trim(),
            quote = draft.quote.replace(Regex("\\s+"), " ").trim()
        )
        val sourceKey = computeSourceKey(play, normalized)
        if (sourceKey == null) {
            return ChroniclePlan(
                memory = normalized.copy(
                    chronicleLine = "",
                    chronicleSourceKey = "",
                    chronicleCreatedAt = null
                ),
                sourceKey = null,
                needsGeneration = false
            )
        }
        val reusedMemory = when {
            normalized.chronicleLine.isNotBlank() && normalized.chronicleSourceKey == sourceKey -> normalized
            existing?.chronicleSourceKey == sourceKey && existing.chronicleLine.isNotBlank() -> normalized.copy(
                chronicleLine = existing.chronicleLine,
                chronicleSourceKey = sourceKey,
                chronicleCreatedAt = existing.chronicleCreatedAt
            )
            else -> normalized.copy(
                chronicleLine = "",
                chronicleSourceKey = sourceKey,
                chronicleCreatedAt = null
            )
        }
        return ChroniclePlan(
            memory = reusedMemory,
            sourceKey = sourceKey,
            needsGeneration = reusedMemory.chronicleLine.isBlank()
        )
    }

    suspend fun compose(play: LoggedPlay, plan: ChroniclePlan, aiConfig: ChronicleAiConfig?): SessionMemory {
        val sourceKey = plan.sourceKey ?: return plan.memory
        val request = ChronicleRequest(
            gameName = play.gameName,
            moods = plan.memory.moods,
            quote = plan.memory.quote,
            playerNames = play.players.map { it.name },
            playerColors = play.players.map { it.color }.filter { it.isNotBlank() }
        )
        val aiLine = aiConfig?.let {
            withTimeoutOrNull(AI_TIMEOUT_MS) { lineGenerator.generate(request, it).getOrNull() }
        }
        val line = aiLine
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trim('"', '\u201c', '\u201d')
            ?.take(MAX_CHRONICLE_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackComposer.compose(request, sourceKey)
        return plan.memory.copy(
            chronicleLine = line,
            chronicleSourceKey = sourceKey,
            chronicleCreatedAt = System.currentTimeMillis()
        )
    }

    private fun computeSourceKey(play: LoggedPlay, memory: SessionMemory): String? {
        val moods = memory.moods.map(::normalizeToken).filter { it.isNotBlank() }.distinct().sorted()
        val quote = normalizeToken(memory.quote)
        if (moods.isEmpty() && quote.isBlank()) return null
        val source = buildString {
            append(normalizeToken(play.gameName))
            append('|')
            append(play.players.map { normalizeToken(it.name) }.filter { it.isNotBlank() }.distinct().sorted().joinToString(","))
            append('|')
            append(play.players.map { normalizeToken(it.color) }.filter { it.isNotBlank() }.distinct().sorted().joinToString(","))
            append('|')
            append(moods.joinToString(","))
            append('|')
            append(quote)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeToken(value: String): String =
        value.lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()

    private companion object {
        private const val AI_TIMEOUT_MS = 45000L
        private const val MAX_CHRONICLE_LENGTH = 110
    }
}
