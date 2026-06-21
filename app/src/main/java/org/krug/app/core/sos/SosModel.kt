package org.krug.app.core.sos

data class SosModel(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val triggeredAt: Long = 0L,
    val message: String? = null,
    /** ID kruga u kome je SOS pokrenut. Null = legacy SOS (pre uvođenja scope-a). */
    val circleId: String? = null,
    /**
     * Friendly ime pošiljaoca, embedovano u payload pri trigger-u. Receiver ne mora da
     * čeka observeUser fetch (koji ume da timeout-uje na 2s i vrati prazan string,
     * što daje "Član" generic fallback umesto pravog imena). Null = legacy SOS.
     */
    val senderName: String? = null,
    /** Friendly naziv kruga u kome je pokrenut — za bogatiji notifikacijski body. */
    val circleName: String? = null,
)
