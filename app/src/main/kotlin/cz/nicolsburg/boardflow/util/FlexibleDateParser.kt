package cz.nicolsburg.boardflow.util

import java.time.LocalDate

private val yearMonthDayPattern = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
private val dayMonthYearPattern = Regex("""^(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{2}|\d{4}))?[-/.]?$""")

fun parseFlexibleLocalDate(
    input: String,
    referenceDate: LocalDate = LocalDate.now()
): LocalDate? {
    val normalized = input.trim().replace("\\s+".toRegex(), "")
    if (normalized.isBlank()) return null

    yearMonthDayPattern.matchEntire(normalized)?.destructured?.let { (year, month, day) ->
        return runCatching {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        }.getOrNull()
    }

    dayMonthYearPattern.matchEntire(normalized)?.let { match ->
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val yearToken = match.groupValues[3]
        val year = when {
            yearToken.isBlank() -> referenceDate.year
            yearToken.length == 2 -> 2000 + yearToken.toInt()
            else -> yearToken.toInt()
        }
        return runCatching {
            LocalDate.of(year, month, day)
        }.getOrNull()
    }

    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

fun String.toFlexibleLocalDateOrNull(referenceDate: LocalDate = LocalDate.now()): LocalDate? =
    parseFlexibleLocalDate(this, referenceDate)
