package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Used to decide which thread pool a coroutine runs on.
 * "CoroutineContext determines thread a coroutine will run on" ?
 * Dispatchers.Main is where UI work must be done (must not be blocked or app frozen)
 * Dispatchers.Default is for CPU-intensive tasks
 *      Thread count = number of CPU cores on machine, or 2 (minimum)
 * Dispatchers.IO is for I/O (or other) tasks that block the thread
 *      A bigger pool of threads, than Default
 * Suspending vs Blocking...(need to understand better)
 */
@DelicateCoroutinesApi
class DispatchersTest {

    @Test
    fun `test Dispatcher implements CoroutineContext interface`() {
        val customDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
        assertTrue(customDispatcher is CoroutineContext.Element)
        assertTrue(customDispatcher is CoroutineContext)
    }

    @Test
    fun `test Dispatchers-Default is chosen if no dispatcher set`() {
        GlobalScope.launch {
            val threadName = Thread.currentThread().name
            print("threadName=$threadName")
            assertTrue(threadName.contains("DefaultDispatcher-worker-"))
        }
    }

    @Test
    fun `test runBlocking sets something other than Dispatchers-Default`() {
        runBlocking {
            val threadName = Thread.currentThread().name
            print("threadName=$threadName")
            assertFalse(threadName.contains("DefaultDispatcher-worker-"))
        }
    }

    @Test
    fun `test Dispatchers-IO shares threads with Dispatchers-Default`() {
        runBlocking {
            var defaultDispatcherThread: String
            var iODispatcherThread: String
            withContext(Dispatchers.Default) {
                defaultDispatcherThread = Thread.currentThread().name
            }
            withContext(Dispatchers.IO) {
                iODispatcherThread = Thread.currentThread().name
            }
            assertEquals(defaultDispatcherThread, iODispatcherThread)
        }
    }

    // Custom dispatchers are useful with code expected to block threads a lot,
    // to limit impact on other areas of the application.
    @Test
    fun `test custom dispatcher with it's own thread pool can be defined`() {
        val numThreads = 20
        val customDispatcher = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()
        var count = 0
        runBlocking {
            launch(customDispatcher) {
                repeat(5) {
                    count++
                }
            }
        }
        customDispatcher.close()
        assertEquals(5, count)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `test Dispatcher-limitedParallelism is a better way to limit number of coroutines ran at once`() {
        runBlocking {
            val dispatcher = Dispatchers.IO.limitedParallelism(2)

            withContext(dispatcher) {
                delay(50)
            }
        }
    }

    @Test
    fun `test Dispatchers-Unconfined never changes threads, but runs on whatever thread it is started or resumed on`() {
        // best performance, since no thread-switching overhead
        runBlocking {
            var continuation: Continuation<Unit>? = null
            var threadName1 = ""
            var threadName2 = ""
            var threadName3 = ""

            launch(newSingleThreadContext("First Launch")) {
                println("1st launch, delaying for a sec")
                delay(1000)
                println("done with 1st launch delay") //FIXME this line never executes
                continuation?.resume(Unit)
            }

            launch(Dispatchers.Unconfined) {
                threadName1 = Thread.currentThread().name
                println("2nd launch, threadName1=$threadName1, suspending coroutine")
                suspendCoroutine<Unit> {
                    println("setting continuation to non-null value=$it") //FIXME last output seen
                    continuation = it
                }
                println("2nd launch, resuming coroutine") //FIXME never resumed from here
                threadName2 = Thread.currentThread().name
                println("threadName2=$threadName2")
                delay(500)
                threadName3 = Thread.currentThread().name
                println("threadName3=$threadName3")
            }
            assertEquals("Name1", threadName1)
            assertEquals("Name2", threadName2)
            assertEquals("kotlinx.coroutines.DefaultExecutor", threadName3)
        }
    }
}