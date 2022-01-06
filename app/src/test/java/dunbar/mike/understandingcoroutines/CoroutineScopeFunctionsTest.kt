package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

@DelicateCoroutinesApi
class CoroutineScopeFunctionsTest {

    private suspend fun networkFetch1(): Int {
        delay(50)
        return 5
    }

    private suspend fun networkFetch2(): Int {
        delay(50)
        return 5
    }

    // Without using scope functions
    @Test
    fun `test calling 2 suspend functions is sequential, not concurrent`() =
        runBlocking {
            val startTime = System.currentTimeMillis()
            val sum = networkFetch1() + networkFetch2()
            val elapsedTime = System.currentTimeMillis() - startTime
            assertEquals(10, sum)
            assertTrue(elapsedTime >= 100)
        }

    @Test
    fun `test wrapping calls to 2 suspend functions with async GlobalScope is concurrent, but loses relationship with parent coroutine`() =
        runBlocking {
            lateinit var part1: Deferred<Int>
            lateinit var part2: Deferred<Int>
            val startTime = System.currentTimeMillis()
            val parent = launch(CoroutineName("Billy")) {
                assertEquals("Billy", coroutineContext[CoroutineName]?.name)
                part1 = GlobalScope.async {
                    assertNotEquals(
                        "Billy",
                        coroutineContext[CoroutineName]?.name
                    ); networkFetch1()
                }
                part2 = GlobalScope.async {
                    assertNotEquals(
                        "Billy",
                        coroutineContext[CoroutineName]?.name
                    ); networkFetch2()
                }
                delay(200)
            }
            delay(1)
            parent.cancelAndJoin()
            assertTrue(parent.isCancelled)
            assertFalse(part1.isCancelled)
            assertFalse(part2.isCancelled)
            assertEquals(10, part1.await() + part2.await())
            val elapsedTime = System.currentTimeMillis() - startTime
            println("elapsedTime=$elapsedTime")
            assertTrue(elapsedTime < 75)
        }

    @Test
    fun `test passing scope to async is concurrent and has relationship to parent, but an exception anywhere breaks entire scope`() {
        val callWithScope = { scope: CoroutineScope,
                              toCall: suspend () -> Int ->
            scope.async {
                assertEquals("Billy", coroutineContext[CoroutineName]?.name); toCall()
            }
        }

        runBlocking {
            lateinit var part1: Deferred<Int>
            lateinit var part2: Deferred<Int>
            val parent = launch(CoroutineName("Billy")) {
                assertEquals("Billy", coroutineContext[CoroutineName]?.name)
                part1 = callWithScope(this, ::networkFetch1)
                part2 = callWithScope(this, ::networkFetch2)
                delay(200)
            }
            delay(1)
            parent.cancelAndJoin()
            assertTrue(parent.isCancelled)
            assertTrue(part1.isCancelled)
            assertTrue(part2.isCancelled)
            try {
                assertEquals(5, part2.await())
                fail("Expected exception not throw")
            } catch (ce: CancellationException) {
                // pass
            }
        }
    }

    // Using scope functions
    @Test
    fun `test coroutineScope calls it's block arg immediately and suspends the existing coroutine`() =
        runBlocking {
            val startTime = System.currentTimeMillis()
            val a = coroutineScope {
                    delay(50)
                10
            }
            val b = coroutineScope {
                delay(50)
                10
            }
            val elapsedTime = System.currentTimeMillis() - startTime
            assertEquals(20, a + b)
            assertTrue(elapsedTime >= 100)
        }

    @Test
    fun `test coroutineScope inherits CoroutineContext from parent, but overrides Job`() =
        runBlocking(CoroutineName("Parent")) {
            val parentName = coroutineContext[CoroutineName]?.name
            val parentJob = coroutineContext[Job]
            var childName: String?
            var childJob: Job?
            coroutineScope {
                childName = coroutineContext[CoroutineName]?.name
                childJob = coroutineContext[Job]
            }
            assertEquals(parentName, childName)
            assertNotEquals(parentJob, childJob)
        }

    @Test
    fun `test coroutineScope waits for all children to complete before completing itself`() {
        runBlocking {
            var parentJob: Job?
            coroutineScope {
                parentJob = coroutineContext[Job]
                val child1 = launch {
                    delay(50)
                }
                val child2 = launch {
                    delay(100)
                }

                assertFalse(parentJob!!.isCompleted)
                delay(75)
                assertTrue(child1.isCompleted)
                assertFalse(child2.isCompleted)
                assertFalse(parentJob!!.isCompleted)

                delay(50)
                assertTrue(child2.isCompleted)
                assertFalse(parentJob!!.isCompleted)
            }
            assertTrue(parentJob!!.isCompleted)
        }
    }

    @Test
    fun `test coroutineScope cancels all children when parent in cancelled`() {
        runBlocking {
            var child1Completed = false
            var child2Completed = false
            var child1Job: Job? = null
            var child2Job: Job? = null
            val parentJob = launch {
                coroutineScope {
                    child1Job = launch {
                        delay(50)
                        child1Completed = true
                    }
                    child2Job = launch {
                        delay(50)
                        child2Completed = true
                    }
                }
            }
            delay(10)
            parentJob.cancelAndJoin()
            assertFalse(child1Completed)
            assertFalse(child2Completed)
            assertTrue(child1Job!!.isCancelled)
            assertTrue(child2Job!!.isCancelled)
        }
    }

    @Test
    fun `test coroutineScope exception cancels all children and rethrows`() {
        runBlocking {
            var child1Completed = false
            var child2Completed = false
            var child1Job: Job? = null
            var child2Job: Job? = null
            var caughtExceptionMsg: String? = "not the message"
            launch {
                try {
                    coroutineScope {
                        child1Job = launch {
                            delay(50)
                            child1Completed = true
                        }
                        child2Job = launch {
                            delay(50)
                            child2Completed = true
                        }
                        delay(10)
                        throw Exception("the message")
                    }
                } catch (e: Exception) {
                    caughtExceptionMsg = e.message
                }
            }
            delay(100)
            assertFalse(child1Completed)
            assertFalse(child2Completed)
            assertTrue(child1Job!!.isCancelled)
            assertTrue(child2Job!!.isCancelled)
            assertEquals("the message", caughtExceptionMsg)
        }
    }

    @Test
    fun `test coroutineScope exception in a child cancels all children and rethrows`() {
        runBlocking {
            var child2Completed = false
            var child1Job: Job? = null
            var child2Job: Job? = null
            var caughtExceptionMsg: String? = "not the message"
            launch {
                try {
                    coroutineScope {
                        child1Job = launch {
                            delay(20)
                            throw Exception("the message")
                        }
                        child2Job = launch {
                            delay(50)
                            child2Completed = true
                        }
                        delay(10)

                    }
                } catch (e: Exception) {
                    caughtExceptionMsg = e.message
                }
            }
            delay(100)
            assertFalse(child2Completed)
            assertTrue(child1Job!!.isCancelled)
            assertTrue(child2Job!!.isCancelled)
            assertEquals("the message", caughtExceptionMsg)
        }
    }

    private suspend fun doMultipleAsyncCalls(): Int = coroutineScope {
        val first = async { networkFetch1() }
        val second = async { networkFetch2() }
        first.await() + second.await()
    }

    @Test
    fun `test coroutineScope is great for multiple async calls in a suspend function`() {
        runBlocking {
            val startTime = System.currentTimeMillis()
            assertEquals(10, doMultipleAsyncCalls())
            val elapsedTime = System.currentTimeMillis() - startTime
            assertTrue(elapsedTime in 51..99)
        }
    }
}