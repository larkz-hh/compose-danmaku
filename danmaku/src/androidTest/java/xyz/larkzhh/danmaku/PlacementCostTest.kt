package xyz.larkzhh.danmaku

import android.util.Log
import java.io.File
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import xyz.larkzhh.danmaku.engine.MeasurementCache
import xyz.larkzhh.danmaku.engine.MeasurementKey
import xyz.larkzhh.danmaku.engine.placeDanmaku

/**
 * What a placement pass costs in time and in retained heap, so the price of measuring a whole list up front
 * is a number rather than a guess.
 *
 * [placeDanmaku] measures every entry whenever the entries, the layer width or the style change, so this is
 * what a screen pays when a long video's danmaku arrives in one go. It is not built on `createComposeRule`:
 * the overlay's frame loop keeps Compose from ever reporting an idle state, which would make the test time
 * out instead of measuring anything.
 *
 * One test per size, on purpose, and each reports as soon as it has its numbers. A run of the whole class is
 * long enough that a device which freezes background processes takes it down mid-test, and an empty
 * `<failure>` with no out-of-memory line behind it is that rather than a heap limit: on a V2415A the class
 * died about ten seconds in with the heap at 61 MB of 256 MB, while the same sizes run one per invocation all
 * completed. Results go to logcat as well as to stdout, so they outlive a process that does not finish.
 *
 * Measured on a V2415A, one size per invocation: 1k entries cost 275 ms and 2.1 MB, 16k cost 2.7 s and 33.6 MB.
 * Retained heap is 2.1 KB per entry at every size, so the limit is the main-thread time, not memory.
 */
@RunWith(AndroidJUnit4::class)
class PlacementCostTest {

    @Test(timeout = 120_000L)
    fun entries1000() = report(1_000)

    @Test(timeout = 120_000L)
    fun entries2000() = report(2_000)

    @Test(timeout = 120_000L)
    fun entries4000() = report(4_000)

    @Test(timeout = 120_000L)
    fun entries8000() = report(8_000)

    @Test(timeout = 120_000L)
    fun entries16000() = report(16_000)

    /** Distinct text again, so a run of these cannot be answered from the measurer's own cache. */
    private fun entries(count: Int, prefix: String) = List(count) { index ->
        DanmakuItem(
            id = index.toLong(),
            text = "$prefix${index}条" + "字".repeat(index % 8),
            timeMs = index * 300L,
        )
    }

    private fun report(count: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // logcat drops this process's output and AGP does not always write the XML report, so the numbers go to
        // a file that can be pulled off the device whatever else happens.
        results = File(context.getExternalFilesDir(null), FILE_NAME).apply { writeText("") }
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(DENSITY),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        val density = Density(DENSITY)
        val style = DanmakuStyle()

        // Distinct text per entry, so the measurer's own cache cannot answer for them: that is what a real
        // danmaku list looks like.
        val items = entries(count, "弹幕内容第")

        // Left out of the measurement: without it the first test in the process reports several times the
        // per-entry cost of the ones after it, because it is the one that pays for the JIT.
        repeat(2) { placeDanmaku(entries(WARMUP, "预热内容第"), CONTAINER_PX, GAP_PX, measurer, density, style) }

        val beforeBytes = retainedBytes()
        log("$count entries: input held ${megabytes(beforeBytes)} MB, heap ${usedHeapMb()} MB")

        // Caught rather than thrown: an entry count that runs the heap out has to leave a number behind, and an
        // uncaught error here takes the whole process with it and reports nothing at all.
        val cold: Long
        val placed: List<*>
        try {
            val start = System.nanoTime()
            placed = placeDanmaku(items, CONTAINER_PX, GAP_PX, measurer, density, style)
            cold = (System.nanoTime() - start) / 1_000_000
        } catch (error: Throwable) {
            log("$count entries: threw ${error.javaClass.name}: ${error.message}, heap ${usedHeapMb()} MB")
            return
        }

        // Collected with `placed` still reachable, so what is left is what the layer keeps alive, not the
        // scratch lists the pass allocated on the way.
        val retainedBytesAfter = retainedBytes()
        val kept = retainedBytesAfter - beforeBytes
        log(
            "$count entries: cold $cold ms (${millis(cold.toDouble() / count)} ms/entry), " +
                "kept ${megabytes(kept)} MB (${kilobytes(kept.toDouble() / count)} KB/entry), " +
                "placed ${placed.size}, heap ${usedHeapMb()} MB",
        )

        System.gc()
        val start = System.nanoTime()
        placeDanmaku(items, CONTAINER_PX, GAP_PX, measurer, density, style)
        val warm = (System.nanoTime() - start) / 1_000_000
        log("$count entries: second pass $warm ms, heap ${usedHeapMb()} MB")

        // The case the cache exists for. Danmaku arrive one at a time, so a list that grew by one entry is the
        // pass the overlay actually reruns; without a cache that outlives a pass it costs a full re-measure.
        val cache = MeasurementCache<MeasurementKey, TextLayoutResult>()
        placeDanmaku(items, CONTAINER_PX, GAP_PX, measurer, density, style, cache)
        val grown = items + DanmakuItem(id = count.toLong(), text = "新到的一条", timeMs = count * 300L)
        val appendStart = System.nanoTime()
        placeDanmaku(grown, CONTAINER_PX, GAP_PX, measurer, density, style, cache)
        val append = (System.nanoTime() - appendStart) / 1_000_000
        log(
            "$count entries: growing by one entry re-placed in $append ms " +
                "(vs $cold ms from cold), cache holds ${cache.size} of ${grown.size}",
        )
    }

    /**
     * Bytes the heap holds once everything unreachable has been collected.
     *
     * The minimum across several collections rather than one reading: a single `System.gc()` does not always
     * finish the job, and one reading too early lands in the middle of a heap the collector has not swept
     * yet, which is how a small list ends up reporting negative retained bytes.
     */
    private fun retainedBytes(): Long {
        val runtime = Runtime.getRuntime()
        var lowest = Long.MAX_VALUE
        repeat(6) {
            System.gc()
            Thread.sleep(30)
            val used = runtime.totalMemory() - runtime.freeMemory()
            if (used < lowest) lowest = used
        }
        return lowest
    }

    private fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0)

    private fun kilobytes(bytes: Double): String = "%.1f".format(bytes / 1024.0)

    private fun millis(value: Double): String = "%.3f".format(value)

    private fun usedHeapMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        println("[$TAG] $message")
        runCatching { results?.appendText("$message\n") }
    }

    private var results: File? = null

    private companion object {
        const val TAG = "PlacementCost"
        const val FILE_NAME = "placement-cost.txt"
        const val WARMUP = 200
        const val DENSITY = 3.5f
        const val CONTAINER_PX = 1260f
        const val GAP_PX = 56f
    }
}
