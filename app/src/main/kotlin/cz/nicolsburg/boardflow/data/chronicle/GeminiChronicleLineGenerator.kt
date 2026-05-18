package cz.nicolsburg.boardflow.data.chronicle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiChronicleLineGenerator : ChronicleLineGenerator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Models confirmed to have zero quota this session — skip key rotation for these.
    private val zeroQuotaModels = mutableSetOf<String>()

    override suspend fun generate(request: ChronicleRequest, config: ChronicleAiConfig): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val allKeys = listOf(config.apiKey) + config.availableApiKeys
                var currentModel = config.modelName
                var currentKeyIndex = 0
                var attempts = 0

                while (attempts < MAX_ATTEMPTS) {
                    attempts++
                    val currentApiKey = allKeys[currentKeyIndex]
                    val endpoint = if (currentModel.contains("/")) currentModel else "v1beta/models/$currentModel"
                    val url = "https://generativelanguage.googleapis.com/$endpoint:generateContent?key=$currentApiKey"
                    val requestBody = buildRequestBody(request)
                    logGemini("request chronicle attempt=$attempts/$MAX_ATTEMPTS model=$currentModel key=${currentKeyIndex + 1}/${allKeys.size} url=${redactApiKey(url)} body=${compactJson(requestBody)}")
                    val httpRequest = Request.Builder()
                        .url(url)
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = client.newCall(httpRequest).execute()
                    val body = response.body?.string().orEmpty()
                    logGemini("response chronicle attempt=$attempts/$MAX_ATTEMPTS model=$currentModel code=${response.code} body=${compactJson(body)}")
                    when {
                        response.isSuccessful -> return@runCatching parseChronicleLine(body)
                        response.code == 429 || response.code == 503 -> {
                            if (attempts >= MAX_ATTEMPTS) throw IllegalStateException("All models are currently experiencing high demand (${response.code}). Please try again in a moment.")
                            // "limit: 0" means the model has no free-tier quota at all — rotating
                            // keys won't help, go straight to the next model.
                            val noQuota = hasZeroQuota(body)
                            if (noQuota) {
                                logGemini("zero-quota chronicle model=$currentModel — skipping key rotation")
                                zeroQuotaModels += currentModel
                            }
                            val nextKeyIndex = currentKeyIndex + 1
                            if (!noQuota && nextKeyIndex < allKeys.size) {
                                logGemini("rotate-key chronicle http=${response.code} model=$currentModel key=${nextKeyIndex + 1}/${allKeys.size} attempt=$attempts/$MAX_ATTEMPTS")
                                currentKeyIndex = nextKeyIndex
                                delay(1000)
                                continue
                            }
                            val nextModel = findNextModel(currentModel, config.availableModels)
                            if (nextModel != null) {
                                logGemini("rotate-model chronicle http=${response.code} from=$currentModel to=$nextModel resetKey=1/${allKeys.size} attempt=$attempts/$MAX_ATTEMPTS")
                                config.onModelExhausted?.invoke(currentModel)
                                currentModel = nextModel
                                currentKeyIndex = 0
                                delay(1000)
                                continue
                            }
                            throw IllegalStateException("All models are currently experiencing high demand (${response.code}). Please try again in a moment.")
                        }
                        else -> throw IllegalStateException("Chronicle API error ${response.code} (using $currentModel):\n$body")
                    }
                }

                throw IllegalStateException("Chronicle failed after $MAX_ATTEMPTS attempts")
            }
        }

    private fun buildRequestBody(request: ChronicleRequest): String {
        val prompt = """
        Write one short chronicle line for a remembered board game session.

        The line should feel:
        - natural
        - grounded
        - memorable
        - concise
        - human

        The best lines feel like remembered fragments from the table, not reviews or prose.

        Prefer:
        - texture
        - pacing
        - reactions
        - atmosphere
        - table feeling

        Use the provided details creatively but carefully.

        You MAY:
        - lightly transform mood words into natural language
        - lightly infer atmosphere from the game title if obvious
        - use one subtle metaphor
        - use the quote directly if it feels memorable
        - sound conversational
        - sound slightly dry or playful

        You MUST NOT:
        - invent gameplay events
        - invent lore or fantasy story details
        - sound epic or cinematic
        - sound like marketing copy
        - explain the game
        - explain the mood
        - summarize the session
        - use generic board game filler phrases

        Avoid phrases like:
        - epic battle
        - unforgettable adventure
        - twists and turns
        - victory was claimed
        - dice rolled
        - legendary
        - competitive energy
        - exciting game
        - thrilling session

        Good examples:
        {"chronicleLine":"Fast hands and a table that never slowed down."}
        {"chronicleLine":"The current carried this one faster than expected."}
        {"chronicleLine":"Martin’s verdict came early: “I didn’t like it.”"}
        {"chronicleLine":"One of those sessions that stayed sharp all night."}
        {"chronicleLine":"Quick turns, loud reactions, immediate rematch energy."}
        {"chronicleLine":"The table barely paused between turns."}
        {"chronicleLine":"Shan and Marsh barely let the table breathe."}

        Bad examples:
        {"chronicleLine":"A legendary battle unfolded through twists and turns."}
        {"chronicleLine":"A competitive and exciting game was enjoyed by all."}
        {"chronicleLine":"Heroes embarked on an unforgettable adventure."}
        {"chronicleLine":"The competitive energy flowed throughout the session."}

        Rules:
        - One sentence only
        - Prefer 50-90 characters
        - Hard max 120 characters
        - Return ONLY valid JSON
        - Format exactly:
          {"chronicleLine":"..."}

        Input:
        Game: ${request.gameName.ifBlank { "Unknown" }}
        Moods: ${request.moods.joinToString(", ").ifBlank { "None" }}
        Quote: ${request.quote.ifBlank { "None" }}
        Players: ${request.playerNames.joinToString(", ").ifBlank { "None" }}
        Colors: ${request.playerColors.joinToString(", ").ifBlank { "None" }}
    """.trimIndent()

        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", prompt)
                        )
                    )
                )
            )

            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 1.0)
                    put("topP", 0.95)
                    put("maxOutputTokens", 96)
                    put("responseMimeType", "application/json")
                }
            )
        }.toString()
    }

    private fun parseChronicleLine(responseJson: String): String {
        val root = JSONObject(responseJson)
        val rawText = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return JSONObject(rawText)
            .optString("chronicleLine", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    private fun findNextModel(currentModel: String, availableModels: List<String>): String? {
        if (availableModels.isEmpty()) return null
        val eligible = availableModels.filter { it !in zeroQuotaModels }
        val sorted = eligible.sortedWith(compareByDescending { it.startsWith("gemini") })
        val currentIndex = sorted.indexOf(currentModel)
        return when {
            currentIndex >= 0 && currentIndex < sorted.lastIndex -> sorted[currentIndex + 1]
            currentIndex == -1 && sorted.isNotEmpty() -> sorted.first()
            else -> null
        }
    }

    private fun hasZeroQuota(body: String): Boolean = body.contains("limit: 0")

    private companion object {
        private const val TAG = "Chronicle"
        private const val MAX_ATTEMPTS = 10
    }

    private fun logGemini(message: String) {
        val full = "GEMINI $message"
        if (full.length <= 3800) {
            Log.d(TAG, full)
        } else {
            val chunks = full.chunked(3800)
            chunks.forEachIndexed { i, chunk -> Log.d(TAG, "[${i + 1}/${chunks.size}] $chunk") }
        }
    }

    private fun redactApiKey(url: String): String = url.replace(Regex("key=[^&]+"), "key=REDACTED")

    private fun compactJson(text: String): String = try {
        JSONObject(text).toString()
    } catch (_: Exception) {
        try {
            JSONArray(text).toString()
        } catch (_: Exception) {
            text.replace(Regex("\\s+"), " ").trim()
        }
    }
}
