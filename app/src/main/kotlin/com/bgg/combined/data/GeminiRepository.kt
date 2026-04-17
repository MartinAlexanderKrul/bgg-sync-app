package com.bgg.combined.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.bgg.combined.model.ExtractedPlay
import com.bgg.combined.model.PlayerResult
import kotlinx.coroutines.Dispatchers
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

    private val RESTRICTED_MODELS = setOf("gemini-2.5-flash-preview-tts", "gemma-3-1b-it")

    suspend fun extractScoresFromImage(
        imageFile: File,
        apiKey: String,
        modelName: String = "gemini-flash-latest",
        availableModels: List<String> = emptyList(),
        onModelChanged: ((String) -> Unit)? = null
    ): Result<ExtractedPlay> = withContext(Dispatchers.IO) {
        runCatching {
            val base64Image = encodeImageToBase64(imageFile)
            val requestBody = buildRequestJson(base64Image)
            var currentModel = modelName; var attempts = 0; val maxAttempts = 10
            while (attempts < maxAttempts) {
                attempts++
                val fullEndpoint = if (currentModel.contains("/")) currentModel else "v1beta/models/$currentModel"
                val url = "https://generativelanguage.googleapis.com/$fullEndpoint:generateContent?key=$apiKey"
                val request = Request.Builder().url(url).post(requestBody.toRequestBody("application/json".toMediaType())).build()
                val response = client.newCall(request).execute()
                val responseText = response.body?.string() ?: throw Exception("Empty response from Gemini API")
                when {
                    response.isSuccessful -> return@runCatching parseGeminiResponse(responseText)
                    response.code == 503 || response.code == 429 -> {
                        val nextModel = findNextModel(currentModel, availableModels)
                        if (nextModel != null && attempts < maxAttempts) { currentModel = nextModel; onModelChanged?.invoke(nextModel); kotlinx.coroutines.delay(1000); continue }
                        else throw Exception("All models are currently experiencing high demand (${response.code}). Please try again in a moment.")
                    }
                    else -> throw Exception("Gemini API error ${response.code} (using $currentModel):\n$responseText")
                }
            }
            throw Exception("Failed after $maxAttempts attempts")
        }
    }

    private fun findNextModel(currentModel: String, availableModels: List<String>): String? {
        if (availableModels.isEmpty()) return null
        val sortedModels = availableModels.sortedWith(compareByDescending { it.startsWith("gemini") })
        val currentIndex = sortedModels.indexOf(currentModel)
        return when {
            currentIndex >= 0 && currentIndex < sortedModels.size - 1 -> sortedModels[currentIndex + 1]
            currentIndex == -1 && sortedModels.isNotEmpty() -> sortedModels[0]
            else -> null
        }
    }

    suspend fun listAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoints = listOf("v1beta", "v1")
            for (apiVersion in endpoints) {
                try {
                    val url = "https://generativelanguage.googleapis.com/$apiVersion/models?key=$apiKey"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val responseText = response.body?.string() ?: ""
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
                                        if (supportedMethods.getString(j).contains("generateContent")) { supportsGenerate = true; break }
                                    }
                                    if (supportsGenerate && name.isNotBlank() && !RESTRICTED_MODELS.contains(name)) modelList.add(name)
                                }
                            }
                            if (modelList.isNotEmpty()) return@runCatching modelList
                        }
                    }
                } catch (e: Exception) { /* Try next */ }
            }
            throw Exception("Could not retrieve model list. Visit https://aistudio.google.com to check available models.")
        }
    }

    private fun encodeImageToBase64(file: File): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sampleSize = 1; val maxDimension = 800
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) sampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildRequestJson(base64Image: String): String {
        val prompt = """
            You are a board game score extractor. I will show you a photo of a handwritten score sheet.
            Extract all player names and their final scores.

            RULES:
            - Return ONLY valid JSON, no preamble, explanation, or markdown fences.
            - The JSON must have exactly this shape:
              {
                "date": "YYYY-MM-DD",
                "players": [
                  { "name": "Alice", "score": "42", "isWinner": true },
                  { "name": "Bob",   "score": "35", "isWinner": false }
                ]
              }
            - isWinner: true for the player(s) with the highest score, or explicitly marked as winner.
            - If scores span multiple rounds, sum them into a single final score.
            - If a name or score is illegible, use your best guess and add a "?" suffix.
            - scores should be numeric strings when possible (e.g. "42").
            - If no date is visible, use today's date.
        """.trimIndent()
        val parts = JSONArray().apply {
            put(JSONObject().apply { put("text", prompt) })
            put(JSONObject().apply { put("inlineData", JSONObject().apply { put("mimeType", "image/jpeg"); put("data", base64Image) }) })
        }
        return JSONObject().apply {
            put("contents", JSONArray().apply { put(JSONObject().apply { put("parts", parts) }) })
            put("generationConfig", JSONObject().apply { put("temperature", 0.1); put("maxOutputTokens", 1024) })
        }.toString()
    }

    private fun parseGeminiResponse(responseJson: String): ExtractedPlay {
        val root = JSONObject(responseJson)
        val rawText = root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        var cleaned = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (!cleaned.endsWith("}")) {
            val openBraces = cleaned.count { it == '{' }; val closeBraces = cleaned.count { it == '}' }
            if (openBraces > closeBraces) cleaned += "}".repeat(openBraces - closeBraces)
        }
        return try {
            val parsed = JSONObject(cleaned)
            val playersArray = parsed.getJSONArray("players")
            val players = (0 until playersArray.length()).map { i ->
                val p = playersArray.getJSONObject(i)
                PlayerResult(name = p.getString("name"), score = p.getString("score"), isWinner = p.optBoolean("isWinner", false))
            }
            val date = if (parsed.has("date")) parsed.optString("date", null) else null
            ExtractedPlay(players = players, rawText = rawText, date = date)
        } catch (e: Exception) {
            val partialPlayers = tryExtractPartialPlayers(cleaned)
            ExtractedPlay(players = partialPlayers, rawText = "⚠️ Incomplete/malformed response (tried to parse anyway):\n$rawText", date = null)
        }
    }

    private fun tryExtractPartialPlayers(text: String): List<PlayerResult> {
        val players = mutableListOf<PlayerResult>()
        try {
            val names = """"name"\s*:\s*"([^"]+)"""".toRegex().findAll(text).map { it.groupValues[1] }.toList()
            val scores = """"score"\s*:\s*"([^"]+)"""".toRegex().findAll(text).map { it.groupValues[1] }.toList()
            val winners = """"isWinner"\s*:\s*(true|false)""".toRegex().findAll(text).map { it.groupValues[1] == "true" }.toList()
            for (i in names.indices) {
                players.add(PlayerResult(name = names.getOrNull(i) ?: "Player ${i+1}", score = scores.getOrNull(i) ?: "0", isWinner = winners.getOrNull(i) ?: false))
            }
        } catch (e: Exception) { /* Return empty */ }
        return players
    }
}
