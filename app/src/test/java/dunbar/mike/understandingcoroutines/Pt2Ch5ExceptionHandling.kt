package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class Pt2Ch5ExceptionHandling {
    private val ints = Collections.synchronizedList(mutableListOf<Int>())

    @Test
    fun `child job exceptions cancel their parent`() = runBlocking {
        // Stop exception propagation at top level, so we can assert things
        supervisorScope {
            val parentJob = launch {
                delay(50)
                launch {
                    throw Exception("Child Exception")
                }
            }
            delay(500)
            assertTrue(parentJob.isCancelled)
        }
    }

    @Test
    fun `parent job exceptions cancel all their children`() = runBlocking {
        lateinit var childJob: Job
        lateinit var childJob2: Job
        // Stop exception propagation at top level, so we can assert things
        supervisorScope {
            launch {
                ints.add(0)
                childJob = launch {
                    delay(100)
                    ints.add(1)
                }

                childJob2 = launch {
                    delay(100)
                    ints.add(2)
                }

                delay(50)
                throw Exception("Parent Exception")
            }
            delay(100)
            assertTrue(childJob.isCancelled)
            assertTrue(childJob2.isCancelled)
            assertEquals(listOf(0), ints)
        }
    }

    @Test
    fun `wrapping a coroutine builder with a try-catch doesn't catch`() = runBlocking {
        // Stop exception propagation at top level, so we can assert things
        supervisorScope {
            var caught = false
            try {
                launch {
                    throw java.lang.Exception("Oops")
                }
            } catch (t: Throwable) {
                caught = true
            }
            assertFalse(caught)
        }
    }

    @Test
    fun `supervisor job ignores exceptions in children`() = runBlocking {
        val ints = Collections.synchronizedList(mutableListOf<Int>())

        val supervisorJob = SupervisorJob()
        val supervisorChild1 = launch(supervisorJob) {
            ints.add(1)
            delay(100)
            ints.add(3)
        }

        val supervisorChild2 = launch(supervisorJob) {
            ints.add(2)
            delay(50)
            throw Exception("Whoops")
        }
        delay(150)
        ints.add(4)
        assertEquals(listOf(1, 2, 3, 4), ints)
        assertTrue(supervisorChild2.isCancelled)
        assertFalse(supervisorChild1.isCancelled)
    }

    @Test
    fun `supervisorScope function works similarly to launch with SupervisorJob`() =
        runBlocking {
            val ints = Collections.synchronizedList(mutableListOf<Int>())

            supervisorScope {
                val child1 = launch {
                    ints.add(1)
                    delay(100)
                    ints.add(3)
                }

                val child2 = launch {
                    ints.add(2)
                    delay(50)
                    throw Exception("Whoops")
                }

                delay(150)
                ints.add(4)
                assertEquals(listOf(1, 2, 3, 4), ints)
                assertTrue(child2.isCancelled)
                assertFalse(child1.isCancelled)
            }
        }

    @Test
    fun `await propagates exceptions from async`() = runBlocking {
        supervisorScope {
            val strings = Collections.synchronizedList(mutableListOf<String>())
            var caught = false
            val str1 = async<String> {
                delay(50)
                throw Exception("Whoops")
            }

            val str2 = async {
                delay(100)
                "World"
            }

            try {
                strings.add(str1.await())
            } catch (e: Exception) {
                caught = true
            }

            strings.add(str2.await())
            assertTrue(caught)
            assertEquals(listOf("World"), strings)
        }
    }

    @Test
    fun `CancellationException isn't propagated to parent`() = runBlocking {
        val strings = Collections.synchronizedList(mutableListOf<String>())

        launch {
            launch {
                delay(100)
                strings.add("Hello")
            }
            throw CancellationException("Shit")
        }

        launch {
            delay(50)
            strings.add("World")
        }
        delay(200)
        assertEquals(listOf("World"), strings)
    }
}