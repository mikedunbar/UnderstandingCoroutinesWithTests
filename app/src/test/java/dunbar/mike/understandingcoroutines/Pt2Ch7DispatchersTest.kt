package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.measureTimeMillis

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
class Pt2Ch7DispatchersTest {


    @Test
    @Suppress("ConstantConditionIf")
    fun `test Dispatcher implements CoroutineContext interface`() {
        if(true)
            println("hi")

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
    fun `test Dispatchers-Unconfined never changes threads but runs on whatever thread it is started or resumed on - Perf optimization`() {
        // This dispatcher performs best, because of no thread switching
        var startThreadName = ""
        var firstResumeThreadName = ""
        var secondResumeThreadName = ""

        runBlocking(newSingleThreadContext("Parent Coroutine Thread")) {
            var continuation: Continuation<Unit>? = null

            launch(newSingleThreadContext("Sibling Coroutine Thread")) {
                delay(1000)
                continuation?.resume(Unit)
            }

            launch(Dispatchers.Unconfined) {
                startThreadName = Thread.currentThread().name
                suspendCoroutine<Unit> {
                    continuation = it
                }
                firstResumeThreadName = Thread.currentThread().name
                delay(500)
                secondResumeThreadName = Thread.currentThread().name
            }
        }

        assertTrue(startThreadName.contains("Parent Coroutine Thread"))
        assertTrue(firstResumeThreadName.contains("Sibling Coroutine Thread"))
        assertTrue(secondResumeThreadName.contains("DefaultExecutor"))
    }

    @Test
    @ExperimentalCoroutinesApi
    fun `test Dispatchers-Main-Immediate dispatches only if not already on Main thread`() {
        Dispatchers.setMain(Dispatchers.Default)
        runBlocking(Dispatchers.Main) {
            // run something on Main without immediate dispatching
            val withoutImmediateDispatchTime = measureTimeMillis {
                withContext(Dispatchers.Main) {
                    delay(100)
                }
            }

            // run something on Main WITH immediate dispatch
            val withImmediateDispatchTime = measureTimeMillis {
                withContext(Dispatchers.Main.immediate) {
                    delay(100)
                }

            }
            println(
                "withoutImmediateDispatch=$withoutImmediateDispatchTime" +
                        ", withImmediateDispatch=$withImmediateDispatchTime"
            )
            assertTrue(
                "coroutine dispatched immediately runs faster",
                withImmediateDispatchTime < withoutImmediateDispatchTime
            )
        }
    }
}