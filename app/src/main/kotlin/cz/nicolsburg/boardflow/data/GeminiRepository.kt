package cz.nicolsburg.boardflow.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import cz.nicolsburg.boardflow.model.ExtractedPlay
import cz.nicolsburg.boardflow.model.PlayerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val restrictedModels = setOf("gemini-2.5-flash-preview-tts", "gemma-3-1b-it")

    // Models confirmed to have zero quota this session — skip key rotation for these.
    private val zeroQuotaModels = mutableSetOf<String>()

    companion object {
        private const val TAG = "Gemini"

        // Preferred fallback order for score extraction (multimodal structured output).
        // Models not in this list fall back to alphabetical-gemini-first ordering.
        private val SCORE_EXTRACTION_PRIORITY = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-2.5-flash-preview-05-20",
            "gemini-2.5-flash",
            "gemini-1.5-pro-latest",
            "gemini-1.5-pro"
        )
    }

    suspend fun extractScoresFromImage(
        imageFile: File,
        apiKey: String,
        modelName: String = "gemini-2.0-flash-lite",
        availableModels: List<String> = emptyList(),
        availableApiKeys: List<String> = emptyList(),
        onModelChanged: ((String) -> Unit)? = null,
        onModelExhausted: ((String) -> Unit)? = null,
        onStreamingStarted: (() -> Unit)? = null
    ): Result<ExtractedPlay> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            logGemini("request score-extract start initialModel=$modelName file=${imageFile.name} size=${imageFile.length()}B availableModels=${availableModels.size} availableApiKeys=${availableApiKeys.size}")

            val base64Image = encodeImageToBase64(imageFile)
            val requestBody = buildRequestJson(base64Image)
            val allKeys = listOf(apiKey) + availableApiKeys
            var currentModel = modelName
            var currentKeyIndex = 0
            var attempts = 0
            val maxAttempts = 10

            while (attempts < maxAttempts) {
                attempts++
                val currentApiKey = allKeys[currentKeyIndex]
                val fullEndpoint = if (currentModel.contains("/")) currentModel else "v1beta/models/$currentModel"
                val url = "https://generativelanguage.googleapis.com/$fullEndpoint:streamGenerateContent?alt=sse&key=$currentApiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val attemptStart = System.currentTimeMillis()
                logGemini("request score-extract attempt=$attempts/$maxAttempts model=$currentModel key=${currentKeyIndex + 1}/${allKeys.size} url=${redactApiKey(url)}")

                val response = client.newCall(request).execute()
                val attemptMs = System.currentTimeMillis() - attemptStart

                // For error responses, read the full body for logging/quota detection.
                // For success, read the SSE stream line-by-line to fire onStreamingStarted early.
                if (!response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    logGemini("response score-extract attempt=$attempts/$maxAttempts model=$currentModel code=${response.code} elapsedMs=$attemptMs body=${compactJson(responseText)}")

                    when (response.code) {
                        503, 429 -> {
                            if (attempts >= maxAttempts) {
                                logGemini("failure score-extract http=${response.code} no-fallback attempts=$attempts")
                                throw Exception("All models are currently experiencing high demand (${response.code}). Please try again in a moment.")
                            }
                            val noQuota = hasZeroQuota(responseText)
                            if (noQuota) {
                                logGemini("zero-quota score-extract model=$currentModel — skipping key rotation")
                                zeroQuotaModels += currentModel
                                onModelExhausted?.invoke(currentModel)
                            }
                            val nextKeyIndex = currentKeyIndex + 1
                            if (!noQuota && nextKeyIndex < allKeys.size) {
                                logGemini("rotate-key score-extract http=${response.code} model=$currentModel key=${nextKeyIndex + 1}/${allKeys.size} attempt=$attempts/$maxAttempts")
                                currentKeyIndex = nextKeyIndex
                                if (response.code == 429) delay(minOf(2000L * attempts, 16000L))
                                else delay(2000)
                                continue
                            }
                            val nextModel = findNextModel(currentModel, availableModels)
                            if (nextModel != null) {
                                logGemini("rotate-model score-extract http=${response.code} from=$currentModel to=$nextModel resetKey=1/${allKeys.size} attempt=$attempts/$maxAttempts")
                                if (!noQuota) onModelExhausted?.invoke(currentModel)
                                currentModel = nextModel
                                currentKeyIndex = 0
                                onModelChanged?.invoke(nextModel)
                                if (!noQuota) delay(1000)
                                continue
                            }
                            logGemini("failure score-extract http=${response.code} no-fallback attempts=$attempts")
                            throw Exception("All models are currently experiencing high demand (${response.code}). Please try again in a moment.")
                        }
                        else -> {
                            logGemini("failure score-extract http=${response.code} model=$currentModel attempt=$attempts")
                            throw Exception("Gemini API error ${response.code} (using $currentModel):\n$responseText")
                        }
                    }
                }

                // Successful response — read SSE stream and accumulate text chunks.
                logGemini("response score-extract attempt=$attempts/$maxAttempts model=$currentModel code=${response.code} elapsedMs=${attemptMs}ms streaming=true")
                val source = response.body?.source() ?: throw Exception("Empty response from Gemini API")
                val accumulated = StringBuilder()
                var streamStartFired = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty()) continue
                    try {
                        val chunk = JSONObject(data)
                        val candidates = chunk.optJSONArray("candidates") ?: continue
                        if (candidates.length() == 0) continue
                        val content = candidates.getJSONObject(0).optJSONObject("content") ?: continue
                        val parts = content.optJSONArray("parts") ?: continue
                        if (parts.length() == 0) continue
                        val text = parts.getJSONObject(0).optString("text", "")
                        if (text.isNotEmpty()) {
                            if (!streamStartFired) {
                                streamStartFired = true
                                onStreamingStarted?.invoke()
                                logGemini("stream-started score-extract model=$currentModel attempt=$attempts")
                            }
                            accumulated.append(text)
                        }
                    } catch (_: Exception) {}
                }

                if (accumulated.isNotEmpty()) {
                    val totalMs = System.currentTimeMillis() - startTime
                    logGemini("success score-extract model=$currentModel attempt=$attempts totalMs=$totalMs accumulated=${accumulated.length}chars")
                    return@runCatching parseExtractedJson(accumulated.toString()).copy(modelUsed = currentModel)
                }

                // 200 response but empty stream — treat as transient and rotate model
                logGemini("empty-stream score-extract model=$currentModel attempt=$attempts — rotating")
                val nextModel = findNextModel(currentModel, availableModels)
                if (nextModel != null && attempts < maxAttempts) {
                    currentModel = nextModel
                    currentKeyIndex = 0
                    onModelChanged?.invoke(nextModel)
                    delay(1000)
                    continue
                }
                throw Exception("Received empty response from Gemini API (using $currentModel)")
            }

            val totalMs = System.currentTimeMillis() - startTime
            logGemini("failure score-extract exhausted attempts=$maxAttempts totalMs=$totalMs")
            throw Exception("Failed after $maxAttempts attempts")
        }
    }

    private fun findNextModel(currentModel: String, availableModels: List<String>): String? {
        if (availableModels.isEmpty()) return null
        val eligible = availableModels.filter { it !in zeroQuotaModels }
        // Sort by priority list first, then alphabetical-gemini-first for unknowns
        val priorityIndex = { m: String -> SCORE_EXTRACTION_PRIORITY.indexOf(m).let { if (it < 0) Int.MAX_VALUE else it } }
        val sorted = eligible.sortedWith(compareBy({ priorityIndex(it) }, { !it.startsWith("gemini") }, { it }))
        val currentIndex = sorted.indexOf(currentModel)
        return when {
            currentIndex >= 0 && currentIndex < sorted.size - 1 -> sorted[currentIndex + 1]
            currentIndex == -1 && sorted.isNotEmpty() -> sorted.firstOrNull { it != currentModel }
            else -> null
        }
    }

    private fun hasZeroQuota(body: String): Boolean = body.contains("limit: 0")

    suspend fun listAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            logGemini("request list-models start")
            val endpoints = listOf("v1beta", "v1")
            for (apiVersion in endpoints) {
                try {
                    val url = "https://generativelanguage.googleapis.com/$apiVersion/models?key=$apiKey"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val responseText = response.body?.string() ?: ""
                    logGemini("response list-models api=$apiVersion code=${response.code} body=${compactJson(responseText)}")
                    if (response.isSuccessful) {
                        val json = JSONObject(responseText)
                        val models = json.optJSONArray("models")
                        if (models != null && models.length() > 0) {
                            val modelList = mutableListOf<String>()
                            for (i in 0 until models.length()) {
                                val model = models.getJSONObject(i)
                                val name = model.optString("name", "").removePrefix("models/")
                                val supportedMethods = model.optJSONArray("supportedGenerationMethods")
                                if (supportedMethods != null) {
                                    var supportsGenerate = false
                                    for (j in 0 until supportedMethods.length()) {
                                        if (supportedMethods.getString(j).contains("generateContent")) {
                                            supportsGenerate = true
                                            break
                                        }
                                    }
                                    if (supportsGenerate && name.isNotBlank() && !restrictedModels.contains(name)) {
                                        modelList.add(name)
                                    }
                                }
                            }
                            if (modelList.isNotEmpty()) {
                                logGemini("success list-models api=$apiVersion count=${modelList.size}")
                                return@runCatching modelList
                            }
                        }
                    } else {
                        logGemini("failure list-models api=$apiVersion http=${response.code}")
                    }
                } catch (e: Exception) {
                    logGemini("failure list-models api=$apiVersion error=${e.message}")
                }
            }
            throw Exception("Could not retrieve model list. Visit https://aistudio.google.com to check available models.")
        }
    }

    private fun encodeImageToBase64(file: File): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sampleSize = 1
        val maxDimension = 1200
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildRequestJson(base64Image: String): String {
        val prompt = """
            You are a board game score extractor. I will show you a photo of a handwritten or printed score sheet.
            Extract all player names and their final scores. Also try to identify the board game this score sheet belongs to.

            RULES:
            - Return ONLY valid JSON, no preamble, explanation, or markdown fences.
            - The JSON must have exactly this shape:
              {
                "date": "YYYY-MM-DD",
                "players": [
                  { "name": "Alice", "score": "42", "isWinner": true },
                  { "name": "Bob",   "score": "35", "isWinner": false }
                ],
                "detectedGameTitle": "Wingspan",
                "detectedGameConfidence": 0.85,
                "detectedScoringCategories": ["Birds", "Eggs", "Food", "End-of-round goals"],
                "gameDetectionEvidence": "Score sheet header reads 'Wingspan', columns labeled Birds and Eggs"
              }
            - isWinner: true for the player(s) with the highest score, or explicitly marked as winner.
            - If scores span multiple rounds, sum them into a single final score.
            - If a name or score is illegible, use your best guess and add a "?" suffix.
            - scores should be numeric strings when possible (e.g. "42").
            - date: the date visible on the score sheet in YYYY-MM-DD format. If no date is visible or legible, return null - do NOT invent or guess a date.
            - detectedGameTitle: your best guess at the board game name from any visible text, logo, or scoring structure; null if not determinable.
            - detectedGameConfidence: float 0.0-1.0 reflecting how certain you are about detectedGameTitle; omit or null if title is null.
            - detectedScoringCategories: list of scoring category labels visible on the sheet (column/row headers); empty array if none visible.
            - gameDetectionEvidence: one short sentence describing the visual cues that led to your game identification; null if no identification made.
        """.trimIndent()
        val parts = JSONArray().apply {
            put(JSONObject().apply { put("text", prompt) })
            put(
                JSONObject().apply {
                    put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }
                    )
                }
            )
        }
        return JSONObject().apply {
            put("contents", JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("topP", 0.90)
                put("maxOutputTokens", 2048)
                put("responseMimeType", "application/json")
            })
        }.toString()
    }

    // Called directly from the SSE streaming path with the fully accumulated JSON text.
    private fun parseExtractedJson(jsonText: String): ExtractedPlay {
        val cleaned = jsonText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val parsed = JSONObject(cleaned)
            val playersArray = parsed.getJSONArray("players")
            val players = (0 until playersArray.length()).map { i ->
                val p = playersArray.getJSONObject(i)
                PlayerResult(
                    name = p.getString("name"),
                    score = p.getString("score"),
                    isWinner = p.optBoolean("isWinner", false)
                )
            }
            val date = parsed.optString("date").takeIf { it.isNotBlank() && it != "null" }
            val detectedGameTitle = parsed.optString("detectedGameTitle").takeIf { it.isNotBlank() && it != "null" }
            val detectedGameConfidence =
                if (parsed.has("detectedGameConfidence") && !parsed.isNull("detectedGameConfidence")) {
                    parsed.getDouble("detectedGameConfidence").toFloat().coerceIn(0f, 1f)
                } else null
            val detectedScoringCategories = parsed.optJSONArray("detectedScoringCategories")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() } }
                ?: emptyList()
            val gameDetectionEvidence = parsed.optString("gameDetectionEvidence").takeIf { it.isNotBlank() && it != "null" }
            logGemini("parsed score-extract date=$date players=${players.size} game=$detectedGameTitle conf=${detectedGameConfidence?.let { (it * 100).toInt() }} categories=${detectedScoringCategories.size}")
            ExtractedPlay(
                players = players,
                rawText = cleaned,
                date = date,
                detectedGameTitle = detectedGameTitle,
                detectedGameConfidence = detectedGameConfidence,
                detectedScoringCategories = detectedScoringCategories,
                gameDetectionEvidence = gameDetectionEvidence
            )
        } catch (e: Exception) {
            logGemini("parse-error score-extract error=${e.message}")
            val partialPlayers = tryExtractPartialPlayers(cleaned)
            ExtractedPlay(
                players = partialPlayers,
                rawText = "Incomplete or malformed response:\n$jsonText",
                date = null,
                isMalformed = true
            )
        }
    }

    private fun tryExtractPartialPlayers(text: String): List<PlayerResult> {
        val players = mutableListOf<PlayerResult>()
        try {
            val names = """"name"\s*:\s*"([^"]+)"""".toRegex().findAll(text).map { it.groupValues[1] }.toList()
            val scores = """"score"\s*:\s*"([^"]+)"""".toRegex().findAll(text).map { it.groupValues[1] }.toList()
            val winners = """"isWinner"\s*:\s*(true|false)""".toRegex().findAll(text).map { it.groupValues[1] == "true" }.toList()
            for (i in names.indices) {
                players.add(
                    PlayerResult(
                        name = names.getOrNull(i) ?: "Player ${i + 1}",
                        score = scores.getOrNull(i) ?: "0",
                        isWinner = winners.getOrNull(i) ?: false
                    )
                )
            }
        } catch (_: Exception) {
        }
        return players
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
