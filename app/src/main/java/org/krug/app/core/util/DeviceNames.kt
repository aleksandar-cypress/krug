package org.krug.app.core.util

/**
 * Mapira sirovi `Build.MODEL` (npr. "SM-S928B") na ljudski naziv ("Galaxy S24 Ultra").
 * Beta-grupa je dominantno Samsung, pa pokrivamo Galaxy S/A/Z liniju. Ostali
 * brand-ovi (Pixel, Xiaomi novi modeli) već vraćaju friendly ime u Build.MODEL.
 */
object DeviceNames {

    private val samsungPrefixes: List<Pair<String, String>> = listOf(
        // Galaxy S series (S24 → najnoviji unazad)
        "SM-S928" to "Galaxy S24 Ultra",
        "SM-S926" to "Galaxy S24+",
        "SM-S921" to "Galaxy S24",
        "SM-S918" to "Galaxy S23 Ultra",
        "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23",
        "SM-S908" to "Galaxy S22 Ultra",
        "SM-S906" to "Galaxy S22+",
        "SM-S901" to "Galaxy S22",
        "SM-G998" to "Galaxy S21 Ultra",
        "SM-G996" to "Galaxy S21+",
        "SM-G991" to "Galaxy S21",
        "SM-G988" to "Galaxy S20 Ultra",
        "SM-G986" to "Galaxy S20+",
        "SM-G981" to "Galaxy S20",
        // Galaxy A series (Aleksandar koristi A37)
        "SM-A376" to "Galaxy A37 5G",
        "SM-A356" to "Galaxy A35 5G",
        "SM-A346" to "Galaxy A34 5G",
        "SM-A336" to "Galaxy A33 5G",
        "SM-A546" to "Galaxy A54 5G",
        "SM-A536" to "Galaxy A53 5G",
        "SM-A526" to "Galaxy A52",
        "SM-A156" to "Galaxy A15",
        "SM-A146" to "Galaxy A14",
        "SM-A136" to "Galaxy A13",
        // Galaxy Z foldables
        "SM-F956" to "Galaxy Z Fold6",
        "SM-F946" to "Galaxy Z Fold5",
        "SM-F936" to "Galaxy Z Fold4",
        "SM-F926" to "Galaxy Z Fold3",
        "SM-F741" to "Galaxy Z Flip6",
        "SM-F731" to "Galaxy Z Flip5",
        "SM-F721" to "Galaxy Z Flip4",
        "SM-F711" to "Galaxy Z Flip3",
        // Galaxy Note
        "SM-N986" to "Galaxy Note 20 Ultra",
        "SM-N981" to "Galaxy Note 20",
    )

    /** Vraća lepo ime ako je raw poznat (npr. "Samsung SM-S928B" → "Samsung Galaxy S24 Ultra"). */
    fun friendly(raw: String): String {
        if (raw.isBlank()) return raw
        val trimmed = raw.trim()
        // Često je raw već "Samsung SM-…"; ekstraktuj samo model deo
        val modelPart = trimmed.substringAfterLast(' ', missingDelimiterValue = trimmed)
        val brandPart = trimmed.substringBeforeLast(' ', missingDelimiterValue = "")
        val match = samsungPrefixes.firstOrNull { (prefix, _) ->
            modelPart.startsWith(prefix, ignoreCase = true)
        }
        return if (match != null) {
            val brand = if (brandPart.isNotBlank()) brandPart else "Samsung"
            "$brand ${match.second}"
        } else {
            raw
        }
    }
}
