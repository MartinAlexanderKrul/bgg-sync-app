package cz.nicolsburg.boardflow.data

import cz.nicolsburg.boardflow.model.LoggedPlay
import android.net.Uri
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

object PlayShareSerializer {
    private const val Prefix = "BFPLAY1:"
    private const val Type = "boardflow.play"
    private const val Version = 1
    private const val DeepLinkBase = "boardflow://play-import"

    fun encode(play: LoggedPlay): String = Prefix + encodePayload(play)

    fun encodeAsLink(play: LoggedPlay): String =
        "$DeepLinkBase?data=${encodePayload(play)}"

    private fun encodePayload(play: LoggedPlay): String {
        val playJson = JSONObject().apply {
            put("gameId", play.gameId)
            put("gameName", play.gameName)
            put("date", play.date)
            put("durationMinutes", play.durationMinutes)
            put("location", play.location)
            put("comments", play.comments)
            put("quantity", play.quantity)
            put("incomplete", play.incomplete)
            put("nowInStats", play.nowInStats)
            put("players", BackupSerializer.playToJson(play).getJSONArray("players"))
        }
        val payload = JSONObject().apply {
            put("type", Type)
            put("version", Version)
            put("createdAt", Instant.now().toString())
            put("checksum", crc32(playJson.toString()))
            put("play", playJson)
        }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(deflate(payload.toString().toByteArray(Charsets.UTF_8)))
    }

    fun decode(raw: String): Result<LoggedPlay> = runCatching {
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
        val playJson = envelope.optJSONObject("play") ?: error("Missing play data.")
        val expectedChecksum = envelope.optString("checksum")
        require(expectedChecksum == crc32(playJson.toString())) { "QR code data looks corrupted." }

        val normalized = JSONObject(playJson.toString()).apply {
            put("id", UUID.randomUUID().toString())
            put("postedToBgg", false)
            if (!has("date") || optString("date").isBlank()) {
                put("date", LocalDate.now().toString())
            }
        }
        BackupSerializer.jsonToPlay(normalized)
    }

    private fun extractPayload(raw: String): String {
        if (raw.startsWith(Prefix)) return raw.removePrefix(Prefix)
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val dataParam = uri?.getQueryParameter("data")?.takeIf { it.isNotBlank() }
        if (dataParam != null) return dataParam
        if (raw.contains("?data=")) {
            return raw.substringAfter("?data=").substringBefore('&')
        }
        error("This QR code is not a BoardFlow play share.")
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
        val out = java.io.ByteArrayOutputStream(input.size * 4 + 1024)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}
