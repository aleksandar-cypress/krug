package org.krug.app.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/**
 * Testovi za bounded ring buffer koji hvata Timber log linije (1.2.3, task A).
 *
 * Regresija-hvatanje: ako neko slučajno ukine bound (npr. zameni ArrayDeque
 * unbounded listom), memory footprint bi neograničeno rastao — testovi
 * `bafer se drži unutar CAPACITY` i `preliv izbacuje najstarije` bi pali.
 */
class LogRingBufferTest {

    @After fun tearDown() {
        // Buffer je singleton, sledeći test count-uje na čist stanje.
        LogRingBuffer.clear()
    }

    @Test fun `prazan bafer vraca prazan dump`() {
        LogRingBuffer.clear()
        assertThat(LogRingBuffer.dump()).isEmpty()
    }

    @Test fun `append cuva liniju u dump order-u`() {
        LogRingBuffer.clear()
        LogRingBuffer.append('I', tag = "Test", message = "prvo")
        LogRingBuffer.append('W', tag = "Test", message = "drugo")
        val lines = LogRingBuffer.dump()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).contains("prvo")
        assertThat(lines[1]).contains("drugo")
    }

    @Test fun `linija sa null tag-om ne ukljucuje tag prefix`() {
        LogRingBuffer.clear()
        LogRingBuffer.append('I', tag = null, message = "no-tag")
        val line = LogRingBuffer.dump().single()
        // Format: `HH:mm:ss.SSS I: message` (bez tag-a između priority i message)
        assertThat(line).endsWith("I: no-tag")
    }

    @Test fun `linija sa tag-om koristi 'time P tag colon message' format`() {
        LogRingBuffer.clear()
        LogRingBuffer.append('W', tag = "Auth", message = "signout")
        val line = LogRingBuffer.dump().single()
        assertThat(line).endsWith("W Auth: signout")
    }

    @Test fun `bafer se drzi unutar CAPACITY`() {
        LogRingBuffer.clear()
        // 100 iznad kapaciteta — svaki drop treba da baci najstariji.
        repeat(LogRingBuffer.CAPACITY + 100) { i ->
            LogRingBuffer.append('D', tag = null, message = "line $i")
        }
        val lines = LogRingBuffer.dump()
        assertThat(lines).hasSize(LogRingBuffer.CAPACITY)
    }

    @Test fun `preliv izbacuje najstarije (FIFO)`() {
        LogRingBuffer.clear()
        // Zapiši 501 linija (kapacitet + 1) — prva linija ("line 0") mora ispasti.
        repeat(LogRingBuffer.CAPACITY + 1) { i ->
            LogRingBuffer.append('D', tag = null, message = "line $i")
        }
        val lines = LogRingBuffer.dump()
        assertThat(lines.first()).contains("line 1")
        assertThat(lines.last()).contains("line ${LogRingBuffer.CAPACITY}")
    }

    @Test fun `dump lastN vraca poslednjih N linija`() {
        LogRingBuffer.clear()
        repeat(50) { LogRingBuffer.append('I', tag = null, message = "line $it") }
        val lastTen = LogRingBuffer.dump(lastN = 10)
        assertThat(lastTen).hasSize(10)
        assertThat(lastTen.first()).contains("line 40")
        assertThat(lastTen.last()).contains("line 49")
    }

    @Test fun `dump lastN veci od velicine bafera vraca sve`() {
        LogRingBuffer.clear()
        repeat(5) { LogRingBuffer.append('I', tag = null, message = "line $it") }
        val allViaBigLastN = LogRingBuffer.dump(lastN = 1000)
        assertThat(allViaBigLastN).hasSize(5)
    }

    @Test fun `clear brise bafer`() {
        LogRingBuffer.append('I', tag = null, message = "prvo")
        LogRingBuffer.append('I', tag = null, message = "drugo")
        LogRingBuffer.clear()
        assertThat(LogRingBuffer.dump()).isEmpty()
    }
}
