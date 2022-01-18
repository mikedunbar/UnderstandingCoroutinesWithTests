package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

@DelicateCoroutinesApi
class CoroutinesBuildersAndBasicsTest {

    private val strings = Collections.synchronizedList(mutableListOf<String>())

    private suspend fun someSuspendingFun() {
        delay(100)
        println("that was fun")
    }

    @Test
    fun `test suspending function cannot be called from a non-suspending function`() {
        val illegalLambda = {
            //someSuspendingFun() // uncomment to see error
            println("this bad")
        }
        illegalLambda()
    }

    @Test
    fun `test suspending function can be called from another suspending function`() {
        val legalLambda = suspend {
            someSuspendingFun()
            println("this is fine")
        }
        print(legalLambda) // unused warning
    }

    @Test
    fun `test suspending functions can be called from a coroutine`() {
        GlobalScope.launch {
            someSuspendingFun()
            println("this is fine")
        }
    }

    @Test
    fun `test suspending functions can call non-suspending functions`() {
        val nonSuspending = {
            5
        }
        val suspending = suspend {
            nonSuspending() + 2
        }

        var result: Int
        runBlocking {
            result = suspending()
        }
        assertEquals(7, result)
    }

    @Test
    fun `test launch coroutine builder requires a scope`() {
        GlobalScope. // comment out this line, to see error
        launch {
            println("I need a scope")
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `test async coroutine builder requires a scope`() {
        GlobalScope. // comment out this line, to see error
        async {
            5
        }.let { gotten ->
            Thread.sleep(1000)
            assertEquals(gotten.getCompleted(), 5)
        }
    }

    @Test
    fun `test runBlocking coroutine builder DOES NOT require a scope`() {
        val gotten = runBlocking {
            5
        }
        assertEquals(5, gotten)
    }

    @Test
    fun `test launch coroutine builder - delays do not block thread, sleep required to do so`() {
        GlobalScope.launch {
            delay(1000)
            strings.add("World!")
        }
        GlobalScope.launch {
            delay(1000)
            strings.add("World!")
        }
        GlobalScope.launch {
            delay(1000)
            strings.add("World!")
        }
        strings.add("Hello,")
        Thread.sleep(2000) // comment to cause failure
        assertEquals(listOf("Hello,", "World!", "World!", "World!"), strings)
    }

    @Test
    fun `test runBlocking coroutine builder - delays DO block thread`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            delay(1000)
            strings.add("World!")
        }
        runBlocking {
            delay(1000)
            strings.add("World!")
        }
        runBlocking {
            delay(1000)
            strings.add("World!")
        }
        strings.add("Hello,")
        val elapsedTime = System.currentTimeMillis() - startTime

        assertEquals(listOf("World!", "World!", "World!", "Hello,"), strings)
        assert(elapsedTime >= 3000)
    }

    @Test
    fun `test runBlocking (top-level) coroutine builder - delays DO block thread`() = runBlocking {
        val startTime = System.currentTimeMillis()
        launch {
            delay(1000)
            strings.add("World!")
        }
        launch {
            delay(1000)
            strings.add("World!")
        }
        launch {
            delay(1000)
            strings.add("World!")
        }
        strings.add("Hello,")
        delay(1100)
        val elapsedTime = System.currentTimeMillis() - startTime
        assertEquals(listOf("Hello,", "World!", "World!", "World!"), strings)
        assertTrue(elapsedTime in 1100..1500)
    }

    @Test
    fun `test async coroutine builder - DeferredResult's await method suspends until value ready`() =
        runBlocking {
            val startTime = System.currentTimeMillis()
            val resultDeferred: Deferred<Int> = async {
                delay(1000)
                42
            }
            assertTrue(System.currentTimeMillis() - startTime <= 500)
            val result = resultDeferred.await()
            assertEquals(42, result)
            assertTrue(System.currentTimeMillis() - startTime >= 1000)
        }

    @Test
    fun `test async coroutine builder - multiple can be used for parallelization`() = runBlocking {
        val startTime = System.currentTimeMillis()
        val result1 = async {
            delay(1000)
            "Text1"
        }

        val result2 = async {
            delay(3000)
            "Text2"
        }

        val result3 = async {
            delay(2000)
            "Text3"
        }

        delay(3000) // async here would be redundant
        val result4 = "Text4"

        strings.add(result1.await())
        strings.add(result2.await())
        strings.add(result3.await())
        strings.add(result4)
        val elapsedTime = System.currentTimeMillis() - startTime

        assertEquals(listOf("Text1", "Text2", "Text3", "Text4"), strings)
        assertTrue(elapsedTime in 3000..3500) // not like 9000
    }

    @Test
    fun `test runBlocking coroutine builder - (parent) waits for children to complete`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            launch {
                delay(1000)
                strings.add("World!")
            }
            strings.add("Hello,")
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        assertEquals(listOf("Hello,", "World!"), strings)
        assertTrue(elapsedTime in 1000..1500)
    }
}

