package cz.nicolsburg.boardflow.data

import android.net.Uri
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.SessionHub
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

object SessionShareSerializer {
    private const val Prefix = "BFSESS1:"
    private const val Type = "boardflow.session"
    private const val Version = 1
    private const val DeepLinkBase = "boardflow://session-import"

    fun encode(session: SessionHub): String = Prefix + encodePayload(session)

    fun encodeAsLink(session: SessionHub): String =
        "$DeepLinkBase?data=${encodePayload(session)}"

    private fun encodePayload(session: SessionHub): String {
        val playsArray = JSONArray().apply {
            session.plays.forEach { play -> put(BackupSerializer.playToJson(play)) }
        }
        val sessionJson = JSONObject().apply {
            put("title", session.title.orEmpty())
            put("date", session.date)
            put("location", session.location)
            put("plays", playsArray)
        }
        val payload = JSONObject().apply {
            put("type", Type)
            put("version", Version)
            put("createdAt", Instant.now().toString())
            put("checksum", crc32(sessionJson.toString()))
            put("session", sessionJson)
        }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(deflate(payload.toString().toByteArray(Charsets.UTF_8)))
    }

    fun decode(raw: String): Result<List<LoggedPlay>> = runCatching {
        val payload = extractPayload(raw)
        val decodedBytes = Base64.getUrlDecoder().decode(payload)
        val decodedJson = runCatching {
            String(inflate(decodedBytes), Charsets.UTF_8)
        }.getOrElse {
            String(decodedBytes, Charsets.UTF_8)
        }
        val envelope = JSONObject(decodedJson)
        require(envelope.optString("type") == Type) { "Unsupported QR payload type." }
        require(envelope.optInt("version") == Version) { "Unsupported BoardFlow share version." }
        val sessionJson = envelope.optJSONObject("session") ?: error("Missing session data.")
        val expectedChecksum = envelope.optString("checksum")
        require(expectedChecksum == crc32(sessionJson.toString())) { "QR code data looks corrupted." }

        val playsArray = sessionJson.optJSONArray("plays") ?: JSONArray()
        (0 until playsArray.length()).map { i ->
            val playObj = playsArray.getJSONObject(i).apply {
                put("id", UUID.randomUUID().toString())
                put("postedToBgg", false)
            }
            BackupSerializer.jsonToPlay(playObj)
        }
    }

    fun isSessionQr(raw: String): Boolean {
        if (raw.startsWith(Prefix)) return true
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        return uri.scheme == "boardflow" && uri.host == "session-import"
    }

    private fun extractPayload(raw: String): String {
        if (raw.startsWith(Prefix)) return raw.removePrefix(Prefix)
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val dataParam = uri?.getQueryParameter("data")?.takeIf { it.isNotBlank() }
        if (dataParam != null) return dataParam
        if (raw.contains("?data=")) {
            return raw.substringAfter("?data=").substringBefore('&')
        }
        error("This QR code is not a BoardFlow session share.")
    }

    private fun crc32(value: String): String {
        val crc = CRC32()
        crc.update(value.toByteArray(Charsets.UTF_8))
        return crc.value.toString(16)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArray(input.size + 128)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOf(length)
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val output = ByteArray(input.size * 8 + 1024)
        val length = inflater.inflate(output)
        inflater.end()
        return output.copyOf(length)
    }
}
