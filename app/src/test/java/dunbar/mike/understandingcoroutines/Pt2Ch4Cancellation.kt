package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.*

@ExperimentalCoroutinesApi
class Pt2Ch4Cancellation {
    private val ints = Collections.synchronizedList(mutableListOf<Int>())

    @Test
    fun `cancelled Job ends at first suspension point`() {
        runBlocking {
            val job = launch {
                repeat(1_000) {
                    delay(200)
                    ints.add(it)
                }
            }
            delay(1100)
            job.cancel()
            job.join()
            assertEquals(listOf(0, 1, 2, 3, 4), ints)
        }
    }

    @Test
    fun `cancelling job cancels all of its children, but not it's parent`() = runBlocking {
        val grandParent = Job()
        val parent = Job(grandParent)
        val child1 = launch(parent) {}
        val child2 = launch(parent) {}
        parent.cancel()
        assertTrue(parent.isCancelled)
        assertTrue(child1.isCancelled)
        assertTrue(child2.isCancelled)
        assertFalse(grandParent.isCancelled)
    }

    @Test
    fun `cancelled job cannot be used as a parent`() = runBlocking {
        val parent = Job()
        launch(parent) {
            delay(100)
            ints.add(1)
        }
        delay(200)
        parent.cancel()
        parent.join()
        launch(parent) {
            delay(100)
            ints.add(2)
        }
        launch {
            delay(100)
            ints.add(3)
        }
        delay(200)
        assertEquals(listOf(1, 3), ints)
    }

    @Test
    fun `cancel without join invites race conditions`() = runTest {
        val job = launch {
            repeat(100) { count ->
                delay(10)
                ints.add(count)
            }
        }
        delay(41)
        job.cancel()
        //job.join() // comment-out, for sporadic failures. TODO - Doesn't work, need better test
        ints.add(4)
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            ints
        )
    }

    @Test
    fun `cancelAndJoin extension function works nicely`() = runTest{
        val job = launch {
            repeat(100) { count ->
                delay(10)
                ints.add(count)
            }
        }
        delay(41)
        job.cancelAndJoin()
        ints.add(4)
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            ints
        )
    }

    @Test
    fun `cancellation throws exception, enabling resource cleanup in finally`() =
        runTest {
            var caught: CancellationException? = null
            val job = launch {
                try {
                    repeat(100) { count ->
                        delay(10)
                        ints.add(count)
                    }
                } catch (ce: CancellationException) {
                    assertEquals(listOf(0, 1, 2, 3), ints)
                    caught = ce
                } finally {
                    ints.clear()
                }
            }
            delay(41)
            job.cancelAndJoin()
            assertTrue(caught != null)
            assertTrue(ints.isEmpty())
        }

    @Test
    fun `cancellation puts no time limit on cleanup, but throws exception on suspension `() =
        runTest {
            var caught: CancellationException? = null
            val job = launch {
                try {
                    repeat(100) { count ->
                        delay(10)
                        ints.add(count)
                    }
                } finally {
                    // simulate a long running cleanup (kinda), no issue
                    repeat(1_000_000) { count ->
                        ints.add(count)
                    }
                    ints.clear()
                    // try to suspend, get exception
                    try {
                        delay(100)
                    } catch (ce: CancellationException) {
                        caught = ce
                    }
                }
            }
            delay(41)
            job.cancelAndJoin()
            assertTrue(caught != null)
            assertTrue(ints.isEmpty())
        }

    @Test
    fun `withContext NonCancellable allows suspension even after cancellation`() =
        runTest {
            var caught: CancellationException? = null
            val job = launch {
                try {
                    repeat(100) { count ->
                        delay(10)
                        ints.add(count)
                    }
                } finally {
                    ints.clear()
                    // suspend without exception
                    try {
                        withContext(NonCancellable) {
                            delay(100)
                            ints.add(5)
                        }
                    } catch (ce: CancellationException) {
                        fail("shouldn't be here")
                        caught = ce
                    }
                }
            }
            delay(41)
            job.cancelAndJoin()
            assertTrue(caught == null)
            assertEquals(listOf(5), ints)
        }

    @Test
    fun `invokeOnCompletion is another option for cleaning up resources - success example`() =
        runTest {
            val intsBeforeCleanup = mutableListOf<Int>()
            var caught: Throwable? = null

            val job = launch {
                repeat(5) { count ->
                    delay(10)
                    ints.add(count)
                }
            }
            job.invokeOnCompletion { t: Throwable? ->
                caught = t
                intsBeforeCleanup.addAll(ints)
                ints.clear()
            }
            job.join()
            assertTrue(caught == null)
            assertTrue(ints.isEmpty())
            assertEquals(listOf(0, 1, 2, 3, 4), intsBeforeCleanup)
            assertTrue(job.isCompleted)
            assertFalse(job.isCancelled)
        }

    @Test
    fun `invokeOnCompletion is another option for cleaning up resources - cancelled example`() {
        runTest {
            val intsBeforeCleanup = mutableListOf<Int>()
            var caught: Throwable? = null

            val job = launch {
                repeat(5) { count ->
                    delay(10)
                    ints.add(count)
                }
            }
            job.invokeOnCompletion { t: Throwable? ->
                caught = t
                intsBeforeCleanup.addAll(ints)
                ints.clear()
            }
            delay(41)
            job.cancelAndJoin()
            assertTrue(caught is CancellationException)
            assertTrue(ints.isEmpty())
            assertEquals(listOf(0, 1, 2, 3), intsBeforeCleanup)
            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun `cancellation doesn't happen without a suspension point`() = runBlocking {
        val job = launch {
            repeat(100) { count ->
                ints.add(count)
            }
        }
        delay(1) // feels like this shouldn't be needed
        job.cancelAndJoin()
        assertEquals(100, ints.size)
    }

    @Test
    fun `cancellation doesn't happen without a suspension point, and yield suspends`() =
        runBlocking {
            val job = launch {
                repeat(100) { count ->
                    delay(5)
                    yield()
                    ints.add(count)
                }
            }
            delay(20) // feels like shouldn't be needed
            job.cancelAndJoin()
            print("ints.size=${ints.size}")
            assertTrue(ints.size in 1..99)
        }

    // Can't get the below 2 to work, try later

//    @Test
//    fun `cancellation works with ensureActive`() =
//        runBlocking {
//            val ints = Collections.synchronizedList(mutableListOf<Int>())
//            val job = Job()
//            launch(job) {
//                repeat(10) { count ->
//                    Thread.sleep(10)
//                    job.ensureActive()
//                    ints.add(count)
//                }
//            }
//            delay(41)
//            job.cancelAndJoin()
//            assertEquals(listOf(0, 1, 2, 3), ints)
//        }
//
//    @Test
//    fun `cancellation doesn't happen without a suspension point, unless you check job state `() {
//        val ints = Collections.synchronizedList(mutableListOf<Int>())
//        GlobalScope.launch {
//            val job = launch {
//                var count = 0
//                do {
//                    Thread.sleep(10)
//                    ints.add(count)
//                    count++
//                } while (isActive && count < 10)
//            }
//            delay(100)
//            job.cancelAndJoin()
//        }
//        assertEquals(listOf(0, 1, 2, 3), ints)
//    }
}