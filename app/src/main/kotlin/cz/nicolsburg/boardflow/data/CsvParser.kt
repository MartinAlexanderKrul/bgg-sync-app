package cz.nicolsburg.boardflow.data

import android.content.ContentResolver
import android.net.Uri

object CsvParser {

    fun parse(resolver: ContentResolver, uri: Uri): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val headerLine = reader.readLine() ?: return result
            val headers = parseLine(headerLine).map { it.trim().lowercase() }
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = parseLine(line)
                val row = mutableMapOf<String, String>()
                headers.forEachIndexed { i, h -> row[h] = if (i < values.size) values[i].trim() else "" }
                result.add(row)
            }
        }
        return result
    }

    private fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>(); val sb = StringBuilder()
        var inQuotes = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                inQuotes && c == '"' -> inQuotes = false
                !inQuotes && c == '"' -> inQuotes = true
                !inQuotes && c == ',' -> { fields.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}
