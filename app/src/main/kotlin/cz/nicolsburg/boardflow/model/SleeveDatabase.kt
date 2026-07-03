package cz.nicolsburg.boardflow.model

import kotlin.math.abs

enum class SleeveManufacturer(val label: String) {
    AUTO("Auto (best available)"),
    TLAMA_DIAMOND("TLAMA Diamond"),
    PALADIN("Paladin"),
    ULTRA_PRO("Ultra Pro"),
    SAPPHIRE("Sapphire"),
    SLEEVE_KINGS("Sleeve Kings"),
    ARCANE_TINMEN("Arcane Tinmen")
}

data class SleeveEntry(
    val genericName: String,
    val recommendedSize: String,
    val originalSizes: List<String>,
    val tlamaDiamond: String?,
    val paladin: String?,
    val ultraPro: String?,
    val sapphire: String?,
    val sleeveKings: String?,
    val arcaneTinmen: String?
) {
    /** All available (brand, productName) pairs in priority order. */
    val manufacturerOptions: List<Pair<String, String>> = buildList {
        tlamaDiamond?.let { add("TLAMA Diamond" to it) }
        paladin?.let       { add("Paladin" to it) }
        ultraPro?.let      { add("Ultra Pro" to it) }
        sapphire?.let      { add("Sapphire" to it) }
        sleeveKings?.let   { add("Sleeve Kings" to it) }
        arcaneTinmen?.let  { add("Arcane Tinmen" to it) }
    }

    /** Best available option by priority (TLAMA Diamond → Paladin → Ultra Pro → Sapphire → Sleeve Kings → Arcane Tinmen). */
    val preferred: Pair<String, String>? get() = manufacturerOptions.firstOrNull()

    /** Returns the product for the given preferred manufacturer, falling back to best available if not found. */
    fun preferredFor(manufacturer: SleeveManufacturer): Pair<String, String>? = when (manufacturer) {
        SleeveManufacturer.AUTO         -> preferred
        SleeveManufacturer.TLAMA_DIAMOND -> tlamaDiamond?.let { "TLAMA Diamond" to it } ?: preferred
        SleeveManufacturer.PALADIN      -> paladin?.let { "Paladin" to it } ?: preferred
        SleeveManufacturer.ULTRA_PRO    -> ultraPro?.let { "Ultra Pro" to it } ?: preferred
        SleeveManufacturer.SAPPHIRE     -> sapphire?.let { "Sapphire" to it } ?: preferred
        SleeveManufacturer.SLEEVE_KINGS -> sleeveKings?.let { "Sleeve Kings" to it } ?: preferred
        SleeveManufacturer.ARCANE_TINMEN -> arcaneTinmen?.let { "Arcane Tinmen" to it } ?: preferred
    }
}

object SleeveDatabase {

    @Suppress("LongMethod")
    val entries: List<SleeveEntry> = listOf(
        SleeveEntry(
            genericName     = "Standard CCG",
            recommendedSize = "63.5 × 88 mm",
            originalSizes   = listOf("63×88","63.5×88","63.5×88.9","63×88.5","61×88","62.3×87.25","63×89","64×89","64×89.5"),
            tlamaDiamond    = "Diamond Green Standard 63.5×88",
            paladin         = "Percival",
            ultraPro        = "Clear Standard Deck Protector",
            sapphire        = "Green",
            sleeveKings     = "Standard Card Game",
            arcaneTinmen    = "Standard"
        ),
        SleeveEntry(
            genericName     = "Mini Euro",
            recommendedSize = "44 × 68 mm",
            originalSizes   = listOf("44×68","44×67","44×67.5","45×67","43×67"),
            tlamaDiamond    = "Diamond Azure European Mini 45×68",
            paladin         = "Arthur",
            ultraPro        = "Mini European",
            sapphire        = "Azure",
            sleeveKings     = "Mini Euro",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Tiny Euro",
            recommendedSize = "44 × 63 mm",
            originalSizes   = listOf("44×63"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Standard American",
            recommendedSize = "59 × 92 mm",
            originalSizes   = listOf("59×91","59×92","60×92"),
            tlamaDiamond    = "Diamond Blue European Standard 59×92",
            paladin         = "Tristan",
            ultraPro        = "Standard European",
            sapphire        = "Blue",
            sleeveKings     = "Euro",
            arcaneTinmen    = "Large"
        ),
        SleeveEntry(
            genericName     = "Chimera Standard",
            recommendedSize = "57.5 × 89 mm",
            originalSizes   = listOf("57×89","58×89","57×87"),
            tlamaDiamond    = "Diamond Orange Chimera Standard 57.5×89",
            paladin         = "Gawain",
            ultraPro        = null,
            sapphire        = "Orange",
            sleeveKings     = "Standard USA Chimera",
            arcaneTinmen    = "Medium"
        ),
        SleeveEntry(
            genericName     = "American Standard",
            recommendedSize = "56 × 87 mm",
            originalSizes   = listOf("56×87","56.45×86.6","53×86"),
            tlamaDiamond    = "Diamond Purple American Standard 56×87",
            paladin         = null,
            ultraPro        = "Standard American",
            sapphire        = "Purple",
            sleeveKings     = "Standard USA",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "American Mini",
            recommendedSize = "41 × 63 mm",
            originalSizes   = listOf("41×63","41×63.5"),
            tlamaDiamond    = "Diamond Yellow American Mini 41×63",
            paladin         = "Galahad",
            ultraPro        = "Mini American",
            sapphire        = "Yellow",
            sleeveKings     = "Mini USA",
            arcaneTinmen    = "Mini"
        ),
        SleeveEntry(
            genericName     = "Chimera Mini",
            recommendedSize = "43 × 66 mm",
            originalSizes   = listOf("43×66"),
            tlamaDiamond    = "Diamond Red Chimera Mini 43×66",
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Medium Square",
            recommendedSize = "67 × 67 mm",
            originalSizes   = listOf("67×67","65×65"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Large Square",
            recommendedSize = "70 × 70 mm",
            originalSizes   = listOf("70×70","72×72"),
            tlamaDiamond    = "Diamond Black Square 70×70",
            paladin         = "Kai",
            ultraPro        = null,
            sapphire        = "Black",
            sleeveKings     = "Small Square",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Small Square",
            recommendedSize = "63 × 63 mm",
            originalSizes   = listOf("63×63","62×62"),
            tlamaDiamond    = null,
            paladin         = "Marcus",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Wyrmspan Cave",
            recommendedSize = "57 × 57 mm",
            originalSizes   = listOf("57×57"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Large Euro / 7 Wonders",
            recommendedSize = "65 × 100 mm",
            originalSizes   = listOf("65×100","67.5×100"),
            tlamaDiamond    = "Diamond Bronze 65×100",
            paladin         = "Lancelot",
            ultraPro        = null,
            sapphire        = "Bronze",
            sleeveKings     = "Magnum 7 Wonders",
            arcaneTinmen    = "Extra Large"
        ),
        SleeveEntry(
            genericName     = "Euro Large Narrow",
            recommendedSize = "55 × 100 mm",
            originalSizes   = listOf("55×100"),
            tlamaDiamond    = null,
            paladin         = "Gilbert",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Tall Narrow",
            recommendedSize = "61 × 112 mm",
            originalSizes   = listOf("61×112"),
            tlamaDiamond    = "Diamond Fuchsia French Tarot 61×112",
            paladin         = "Gudrun",
            ultraPro        = null,
            sapphire        = "Fuchsia",
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Tarot Narrow",
            recommendedSize = "70 × 120 mm",
            originalSizes   = listOf("70×120","69.78×120.25"),
            tlamaDiamond    = "Diamond Pink Tarot 70×120",
            paladin         = "Bors",
            ultraPro        = "Tarot",
            sapphire        = "Pink",
            sleeveKings     = "WOTR",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "War of the Ring",
            recommendedSize = "67 × 120 mm",
            originalSizes   = listOf("67×120"),
            tlamaDiamond    = null,
            paladin         = "Beorn",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Dixit / Large Tarot",
            recommendedSize = "80 × 120 mm",
            originalSizes   = listOf("79×120","80×120"),
            tlamaDiamond    = "Diamond Gold Dixit 80×120",
            paladin         = "Gaheris",
            ultraPro        = null,
            sapphire        = "Gold",
            sleeveKings     = "Magnum Dixit",
            arcaneTinmen    = "Oversize"
        ),
        SleeveEntry(
            genericName     = "Scythe",
            recommendedSize = "70 × 110 mm",
            originalSizes   = listOf("70×110"),
            tlamaDiamond    = "Diamond Lime Scythe 70×110",
            paladin         = "Lamorac",
            ultraPro        = null,
            sapphire        = "Lime",
            sleeveKings     = "Magnum Lost Cities",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Tiny Epic",
            recommendedSize = "88 × 126 mm",
            originalSizes   = listOf("88×126","87×127"),
            tlamaDiamond    = "Diamond Grey Tiny Epic 88×125",
            paladin         = "Pellinore",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = "Tiny Epic Compatible",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Oversized Portrait",
            recommendedSize = "102 × 146 mm",
            originalSizes   = listOf("102×146"),
            tlamaDiamond    = null,
            paladin         = "Lothar",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Oversized XXL",
            recommendedSize = "101 × 126 mm",
            originalSizes   = listOf("101×126","103×128"),
            tlamaDiamond    = null,
            paladin         = "Ragnelle",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Giant Oversized",
            recommendedSize = "127 × 159 mm",
            originalSizes   = listOf("127×159"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Photo / Tarot XL",
            recommendedSize = "100 × 152 mm",
            originalSizes   = listOf("100×152","101.6×152.4"),
            tlamaDiamond    = null,
            paladin         = "Morgana",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Square Large",
            recommendedSize = "102 × 102 mm",
            originalSizes   = listOf("102×102"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Medium Square Large",
            recommendedSize = "95 × 95 mm",
            originalSizes   = listOf("95×95"),
            tlamaDiamond    = null,
            paladin         = "Thanatos",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Large Custom",
            recommendedSize = "83 × 113 mm",
            originalSizes   = listOf("83×113"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Landscape Tarot",
            recommendedSize = "102 × 76 mm",
            originalSizes   = listOf("102×76"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Portrait Tarot",
            recommendedSize = "76 × 102 mm",
            originalSizes   = listOf("76×102"),
            tlamaDiamond    = null,
            paladin         = "Lucius",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Large Square Premium",
            recommendedSize = "76 × 76 mm",
            originalSizes   = listOf("76×76"),
            tlamaDiamond    = null,
            paladin         = "Trevor",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = "Hogwarts Battle Square",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Square 80",
            recommendedSize = "80 × 80 mm",
            originalSizes   = listOf("80×80"),
            tlamaDiamond    = "Diamond Caramel Square Medium 80×80",
            paladin         = "Owain",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = "Medium Square",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Small Custom",
            recommendedSize = "50 × 75 mm",
            originalSizes   = listOf("50×75","49×72.5"),
            tlamaDiamond    = "Diamond White Through the Ages 50×75",
            paladin         = "Lohengrin",
            ultraPro        = null,
            sapphire        = "White",
            sleeveKings     = "Sails of Glory",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Tiny Custom",
            recommendedSize = "53 × 63 mm",
            originalSizes   = listOf("53×63"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Narrow Tall",
            recommendedSize = "62 × 103.5 mm",
            originalSizes   = listOf("62×103.5"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Small Tarot",
            recommendedSize = "62 × 79 mm",
            originalSizes   = listOf("62×79"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Small Landscape",
            recommendedSize = "58 × 75 mm",
            originalSizes   = listOf("58×75"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "LOTR Duel",
            recommendedSize = "54 × 80 mm",
            originalSizes   = listOf("54×80"),
            tlamaDiamond    = "Diamond Rainbow 54×80",
            paladin         = "Bedivere",
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = "Yucatan",
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Large Board Cards",
            recommendedSize = "91 × 141 mm",
            originalSizes   = listOf("91×141"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "XL Board Cards",
            recommendedSize = "90 × 130 mm",
            originalSizes   = listOf("90×130"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Spirit Island Panels",
            recommendedSize = "230 × 152 mm",
            originalSizes   = listOf("230×152"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Wonder Boards",
            recommendedSize = "110 × 250 mm",
            originalSizes   = listOf("110×250"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "Wingspan Goal Board",
            recommendedSize = "150 × 120 mm",
            originalSizes   = listOf("150×120"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        ),
        SleeveEntry(
            genericName     = "WotR Oversized",
            recommendedSize = "120 × 134 mm",
            originalSizes   = listOf("120×134"),
            tlamaDiamond    = null,
            paladin         = null,
            ultraPro        = null,
            sapphire        = null,
            sleeveKings     = null,
            arcaneTinmen    = null
        )
    )

    /**
     * Matches a size string (e.g. "63.5 x 88 mm" or "63×88") against the database.
     * Uses ±0.6 mm tolerance on each dimension; among all candidates returns the
     * closest match (minimum Manhattan distance) so list order does not determine results.
     * Also tries swapped dimensions to handle landscape vs portrait variants.
     */
    fun findBySize(size: String): SleeveEntry? {
        val query = parseDimensions(size) ?: return null
        val normal  = closestMatch(query)
        val rotated = closestMatch(query.second to query.first)
        return when {
            normal  == null -> rotated?.first
            rotated == null -> normal.first
            else            -> if (normal.second <= rotated.second) normal.first else rotated.first
        }
    }

    private fun closestMatch(q: Pair<Float, Float>): Pair<SleeveEntry, Float>? {
        var best: SleeveEntry? = null
        var bestDist = Float.MAX_VALUE
        for (entry in entries) {
            for (sizeStr in listOf(entry.recommendedSize) + entry.originalSizes) {
                val d = parseDimensions(sizeStr) ?: continue
                val dw = abs(d.first  - q.first)
                val dh = abs(d.second - q.second)
                if (dw < 0.6f && dh < 0.6f) {
                    val dist = dw + dh
                    if (dist < bestDist) { bestDist = dist; best = entry }
                }
            }
        }
        return best?.let { it to bestDist }
    }

    private val DIMENSION_RE = Regex("""(\d+\.?\d*)\s*[×xX]\s*(\d+\.?\d*)""")

    private fun parseDimensions(size: String): Pair<Float, Float>? {
        val m = DIMENSION_RE.find(size) ?: return null
        val w = m.groupValues[1].toFloatOrNull() ?: return null
        val h = m.groupValues[2].toFloatOrNull() ?: return null
        return w to h
    }
}
