package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class Pt2Ch9TheProblemWithStateTest {

    @Test
    fun `test shared state without synchronization won't work`() {
        var counter = 0
        val runCount = 100_000
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(runCount) {
                    launch {
                        counter++
                    }
                }

            }
        }
        println("count=$counter")
        assertTrue(counter < runCount)
    }

    @Test
    fun `test shared state with synchronization lock works, but blocks threads and wastes resources`() {
        var counter = 0
        val lock = Any()
        val runCount = 100_000
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(runCount) {
                    launch {
                        synchronized(lock) {
                            counter++
                        }
                    }
                }
            }
        }
        println("count=$counter")
        assertEquals(runCount, counter)
    }

    @Test
    fun `test shared state with an atomic value works, and is more efficient, but only helps with individual operations`() {
        val counter = AtomicInteger()
        val runCount = 100_000
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(runCount) {
                    launch {
                        counter.incrementAndGet()
                    }
                }
            }
        }
        println("count=$counter")
        assertEquals(runCount, counter.get())
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `test shared state with Dispatcher-limitParallelism(1) works, but loses multi-threading and is inefficient - so prefer fine-grained to course-grained`() {
        val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1)
        var counter = 0
        val runCount = 100_000
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(runCount) {
                    launch(singleThreadDispatcher) {
                        // If we were doing more work here, we'd prefer to only wrap the
                        // increment with the limitedParallelism dispatcher to improve concurrency
                        counter++
                    }
                }
            }
        }
        println("count=$counter")
        assertEquals(runCount, counter)
    }

   @Test
    fun `test shared state with a Mutex works`() {
        var counter = 0
        val runCount = 100_000
        val mutex = Mutex()
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(runCount) {
                    launch {
                        // prefer withLock to manual lock/unlock, since it unlocks in finally
                        // avoiding deadlock if an exception is thrown
                        mutex.withLock {
                            counter++
                        }

                    }
                }
            }
        }
        println("count=$counter")
        assertEquals(runCount, counter)
    }



}