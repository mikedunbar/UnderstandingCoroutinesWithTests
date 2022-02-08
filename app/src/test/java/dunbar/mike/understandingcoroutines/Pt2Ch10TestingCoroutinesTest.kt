package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.system.measureTimeMillis

@ExperimentalCoroutinesApi
class Pt2Ch10TestingCoroutinesTest {
    interface Svc {
        suspend fun doThis()
        suspend fun doThat()
    }

    private suspend fun parallelizedSvcConsumer(svc: Svc) {
        coroutineScope {
            launch { svc.doThis() }
            launch { svc.doThat() }
        }
    }

    private suspend fun sequentialSvcConsumer(svc: Svc) {
        svc.doThis()
        svc.doThat()
    }

    @Test
    fun `faked delays can be used to test timing perf, like parallelization vs sequential`() {
        val fakeDelayingSvc = object : Svc {
            override suspend fun doThis() {
                delay(50)
            }

            override suspend fun doThat() {
                delay(50)
            }
        }
        var parallelConsumerTime: Long
        var sequentialConsumerTime: Long

        runBlocking {
            parallelConsumerTime = measureTimeMillis {
                parallelizedSvcConsumer(fakeDelayingSvc)
            }
            sequentialConsumerTime = measureTimeMillis {
                sequentialSvcConsumer(fakeDelayingSvc)
            }
        }
        println("parallelConsumerTime=$parallelConsumerTime, sequentialConsumerTime=$sequentialConsumerTime")
        assertTrue("parallel execution time between 50 and 9ms", parallelConsumerTime in 50..99)
        assertTrue("sequential execution time at least 100ms", sequentialConsumerTime >= 100)
    }

    @Test
    fun `TestCoroutineScheduler operates on virtual time`() {
        val testScheduler = TestCoroutineScheduler()

        assertEquals(0, testScheduler.currentTime)
        testScheduler.advanceTimeBy(1000)
        assertEquals(1000, testScheduler.currentTime)
    }

    @Test
    fun `StandardTestDispatcher creates a TestCoroutineScheduler by default`() {
        val testDispatcher = StandardTestDispatcher()
        assertTrue(testDispatcher.scheduler is TestCoroutineScheduler)
    }

    @Test
    fun `advanceUntilIdle pushes time and invokes all operations until nothing left to do`() {
        val testDispatcher = StandardTestDispatcher()
        var a = "start"
        CoroutineScope(testDispatcher).launch {
            delay(1000)
            a = "stop"
            delay(1000)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2000, testDispatcher.scheduler.currentTime)
        assertEquals("stop", a)
    }

    @Test
    fun `advanceTimeBy(10) resumes everything delayed by less than 10 millis`() {
        val testDispatcher = StandardTestDispatcher()
        var a = "start"
        var b = "hello"

        CoroutineScope(testDispatcher).launch {
            delay(9)
            a = "end"
            delay(1)
            b = "goodbye"
        }
        testDispatcher.scheduler.advanceTimeBy(10)
        assertEquals("end", a)
        assertEquals("hello", b)
    }

    @Test
    fun `runCurrent resumes everything scheduled for the current time`() {
        val testDispatcher = StandardTestDispatcher()
        var a = "start"
        var b = "hello"

        CoroutineScope(testDispatcher).launch {
            delay(9)
            a = "end"
            delay(1)
            b = "goodbye"
        }
        testDispatcher.scheduler.advanceTimeBy(10)
        assertEquals("end", a)
        assertEquals("hello", b)
        testDispatcher.scheduler.runCurrent()
        assertEquals("goodbye", b)
    }

    @Test
    fun `Thread-sleep() does not influence virtual time`() {
        val testDispatcher = StandardTestDispatcher()
        var completed = false

        CoroutineScope(testDispatcher).launch {
            delay(10)
            completed = true
        }

        val elapsedTime = measureTimeMillis {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(1000)
        }
        val coroutineTime = testDispatcher.scheduler.currentTime
        assertTrue(completed)
        assertEquals(10, coroutineTime)
        assertTrue(elapsedTime > 1000)
    }

    @Test
    fun `TestScope automatically uses StandardTestDispatcher and exposes virtual time methods`() {
        val scope = TestScope()
        var completed = false
        scope.launch {
            delay(9)
            completed = true
        }
        scope.advanceTimeBy(10)
        assertTrue(completed)
        assertEquals(10, scope.currentTime)
    }

    @Test
    fun `runTest start a coroutine with TestScope and advances until idle`() = runTest {
        assertEquals(0, currentTime)
        delay(1000)
        assertEquals(1000, currentTime)
    }

    @Test
    fun `CompletableDeferred wraps Deferred with a complete method, which causes awaiting coroutines to resume`() =
        runTest {
            val deferredString = CompletableDeferred<String>()
            var result = "wrong"
            val job = launch {
                result = deferredString.await()
            }
            delay(1000)
            deferredString.complete("right")
            job.join()
            assertEquals("right", result)
            assertEquals(1000, currentTime)
        }

    interface Repo {
        suspend fun getName(): String
        suspend fun getAge(): Int
    }

    class FakeRepo : Repo {
        val deferredName = CompletableDeferred<String>()
        val deferredAge = CompletableDeferred<Int>()

        override suspend fun getName() = deferredName.await()
        override suspend fun getAge() = deferredAge.await()
    }

    class MySvc(private val repo: Repo) {
        suspend fun getGreeting(): String {
            return coroutineScope {
                val name = async { repo.getName() }
                val age = async { repo.getAge() }
                "Hello ${age.await()}yr old ${name.await()}"
            }
        }
    }

    @Test
    fun `CompletableDeferred let's use move fake test delay from fakes to tests`() = runTest {
        val fakeRepo = FakeRepo()
        val svc = MySvc(fakeRepo)

        launch {
            delay(2000)
            fakeRepo.deferredName.complete("Mike")
        }
        launch {
            delay(1500)
            fakeRepo.deferredAge.complete(10)
        }
        assertEquals("Hello 10yr old Mike", svc.getGreeting())
        assertEquals(2000, currentTime)
    }

    interface CustReader {
        suspend fun read()
    }

    class FakeCustReader : CustReader {
        var threadName: String? = null

        override suspend fun read() {
            threadName = Thread.currentThread().name
        }
    }
    val myFakeCustReader = FakeCustReader()

    @Test
    fun ` to test dispatcher switching, capture thread names in a mock or fake`() {
        val codeToTest: suspend (CustReader) -> Unit = { reader: CustReader ->
            withContext(Dispatchers.IO) {
                reader.read()
            }
        }
        runTest {
            codeToTest.invoke(myFakeCustReader)
            assert(myFakeCustReader.threadName!!.startsWith("DefaultDispatcher-worker-"))
        }
    }

    @Test
    fun `to stay on virtual time and test timing dependencies, inject StandardTestDispatcher`() {
        val codeToTest: suspend (CustReader, CoroutineDispatcher) -> Unit =
            { reader: CustReader, dispatcher: CoroutineDispatcher ->
                withContext(dispatcher) {
                    delay(500)
                    reader.read()
                }
            }

        runTest {
            val testDispatcher = this.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
            codeToTest.invoke(myFakeCustReader, testDispatcher)
            assertTrue(currentTime in 500..799)
        }
    }
}