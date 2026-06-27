package org.krug.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold / Warm / Hot startup vreme za Krug app.
 *
 * Run:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 *
 * Pre run-a uveri se da je `:app` build-ovan u `benchmark` build type (matchingFallback
 * targetira release artefakt — minified, R8 + baseline profile). Macrobench output ide
 * u `benchmark/build/outputs/connected_android_test_additional_output/`.
 *
 * Tipični brojevi za Krug na Pixel 4+:
 * - Cold: 600-900ms (sa baseline profile)
 * - Warm: 300-500ms
 * - Hot: 100-200ms
 *
 * Regresija = bilo koja od ovih granica probijena za >15% između commit-a.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNone() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() = startup(StartupMode.COLD, CompilationMode.Partial())

    @Test
    fun warmStartup() = startup(StartupMode.WARM, CompilationMode.Partial())

    @Test
    fun hotStartup() = startup(StartupMode.HOT, CompilationMode.Partial())

    private fun startup(mode: StartupMode, compilation: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = DEFAULT_ITERATIONS,
            startupMode = mode,
            compilationMode = compilation,
        ) {
            pressHome()
            startActivityAndWait()
        }

    companion object {
        private const val TARGET_PACKAGE = "org.krug.app"
        private const val DEFAULT_ITERATIONS = 5
    }
}
