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
            var parent: Job?
            coroutineScope {
                parent = coroutineContext[Job]
                val child1 = launch {
                    delay(50)
                }
                val child2 = launch {
                    delay(100)
                }

                delay(75)
                assertTrue(child1.isCompleted)
                assertFalse(child2.isCompleted)
                assertFalse(parent!!.isCompleted)

                delay(50)
                assertTrue(child2.isCompleted)
                assertFalse(parent!!.isCompleted)
            }
            assertTrue(parent!!.isCompleted)
        }
    }


}