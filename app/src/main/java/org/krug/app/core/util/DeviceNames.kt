package org.krug.app.core.util

/**
 * Mapira sirovi `Build.MODEL` (npr. "SM-S928B") na ljudski naziv ("Galaxy S24 Ultra").
 * Beta-grupa je Samsung + Xiaomi; pokrivamo Galaxy S/A/Z liniju + popularne Xiaomi/Redmi
 * modele koji vraćaju cryptic kod u Build.MODEL umesto imena.
 */
object DeviceNames {

    /** Xiaomi modeli — Build.MODEL vraća cryptic interni kod (npr. "21081111RG"). */
    private val xiaomiPrefixes: List<Pair<String, String>> = listOf(
        // Mi/Xiaomi flagship
        "23090RA98G" to "Xiaomi 13T",
        "23078RKD5G" to "Xiaomi 13T Pro",
        "23117RA68G" to "Xiaomi 14",
        "2211133G" to "Xiaomi 13",
        "2210132G" to "Xiaomi 13 Pro",
        "2201123G" to "Xiaomi 12",
        "2201122G" to "Xiaomi 12 Pro",
        // Mi 11 series — Lite/NE/5G suffixe izostavljamo (suvišno za prikaz)
        "21081111RG" to "Mi 11",
        "M2101K9AG" to "Mi 11",
        "M2102K1G" to "Mi 11",
        "2107113SG" to "Mi 11T",
        "2107113SI" to "Mi 11T",
        // Redmi Note
        "2201117TG" to "Redmi Note 11",
        "2201116TG" to "Redmi Note 11",
        "22101316G" to "Redmi Note 12",
        "23028RA60L" to "Redmi Note 12 Pro",
        // Poco
        "2104290C" to "POCO F3",
        "M2007J20CG" to "POCO X3",
    )

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

        // Samsung
        samsungPrefixes.firstOrNull { (prefix, _) ->
            modelPart.startsWith(prefix, ignoreCase = true)
        }?.let { (_, name) ->
            val brand = if (brandPart.isNotBlank()) brandPart else "Samsung"
            return "$brand $name"
        }

        // Xiaomi / Redmi / POCO — Build.MANUFACTURER je "Xiaomi" za sve, friendly ime
        // ide bez ponavljanja prefiksa (npr. "Xiaomi Mi 11 Lite", ne "Xiaomi Mi 11 Lite Xiaomi").
        xiaomiPrefixes.firstOrNull { (prefix, _) ->
            modelPart.startsWith(prefix, ignoreCase = true)
        }?.let { (_, name) ->
            // Ako ime već počinje sa Xiaomi/Redmi/POCO, ne dupliraj brand prefiks.
            return if (
                name.startsWith("Xiaomi", true) ||
                name.startsWith("Redmi", true) ||
                name.startsWith("POCO", true) ||
                name.startsWith("Mi ", true)
            ) {
                if (name.startsWith("Mi ", true)) "Xiaomi $name" else name
            } else {
                "Xiaomi $name"
            }
        }

        return raw
    }
}
