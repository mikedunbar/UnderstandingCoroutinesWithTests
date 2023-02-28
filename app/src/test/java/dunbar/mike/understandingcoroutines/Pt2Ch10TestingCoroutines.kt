package dunbar.mike.understandingcoroutines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.Thread.sleep
import kotlin.coroutines.ContinuationInterceptor
import kotlin.system.measureTimeMillis

@ExperimentalCoroutinesApi
class Pt2Ch10TestingCoroutines {
    private interface Svc {
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
    fun `faked delays (in test stubs) can be used to test timing perf, like parallelization vs sequential`() {
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
        assertTrue("parallel execution time between 50 and 99ms", parallelConsumerTime in 50..99)
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
    fun `runTest starts a coroutine with TestScope and advances until idle`() = runTest {
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

    private interface Repo {
        suspend fun getName(): String
        suspend fun getAge(): Int
    }

    private class FakeRepo : Repo {
        val deferredName = CompletableDeferred<String>()
        val deferredAge = CompletableDeferred<Int>()

        override suspend fun getName() = deferredName.await()
        override suspend fun getAge() = deferredAge.await()
    }

    private class MySvc(private val repo: Repo) {
        suspend fun getGreeting(): String {
            return coroutineScope {
                val name = async { repo.getName() }
                val age = async { repo.getAge() }
                "Hello ${age.await()}yr old ${name.await()}"
            }
        }
    }

    @Test
    fun `CompletableDeferred let's use move fake test delay from stubs to tests`() = runTest {
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

    private interface CustReader {
        suspend fun read()
    }

    private class FakeCustReader : CustReader {
        var threadName: String? = null

        override suspend fun read() {
            threadName = Thread.currentThread().name
        }
    }
    private val myFakeCustReader = FakeCustReader()

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
    fun `to stay on virtual time and test timing dependencies in classes that switch dispatcher, inject StandardTestDispatcher`() {
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

    private data class State(var progressBarVisible: Boolean = false)

    @Test
    fun `to test things that happen during function execution, but aren't part of the end state, start the function in a new coroutine and advance virtual time to test points`() {

        val codeToTest: suspend (State) -> Unit = { state: State ->
            state.progressBarVisible = true
            println("delaying")
            delay(100) // do some work
            println("done delaying")
            state.progressBarVisible = false
        }

        runTest {
            val state = State()

            launch {
                codeToTest.invoke(state)
            }
            assertEquals(false, state.progressBarVisible)

            advanceTimeBy(50)
            assertEquals(true, state.progressBarVisible)

            advanceTimeBy(50)
            runCurrent()
            assertEquals(false, state.progressBarVisible)
        }

    }

    private data class Notification(val id: Int)
    private interface NotificationsRepo {
        suspend fun getNotificationsToSend(): List<Notification>
        suspend fun markAsSent(notification: Notification)
    }

    private class FakeNotificationsRepo(
        private val toSend: List<Notification>,
        private val delayMillis: Long
    ): NotificationsRepo {

        val sentNotifications = mutableListOf<Notification>()

        override suspend fun getNotificationsToSend(): List<Notification> {
            delay(delayMillis)
            return toSend
        }

        override suspend fun markAsSent(notification: Notification) {
            delay(delayMillis)
            sentNotifications.add(notification)
        }
    }

    private interface NotificationService {
        suspend fun sendNotification(notification: Notification)
    }

    private class FakeNotificationService(
        private val delayMillis: Long
    ) : NotificationService {

        val sentNotifications = mutableListOf<Notification>()

        override suspend fun sendNotification(notification: Notification) {
            delay(delayMillis)
            sentNotifications.add(notification)
        }
    }

    @Test
    fun `to test a function that starts a new coroutine (parallelism), inject TestScope and mock dependencies with artificial delays`() {
        val codeToTest: (NotificationsRepo, NotificationService, CoroutineScope) -> Unit = {
            repo: NotificationsRepo, svc: NotificationService, scope: CoroutineScope ->

            scope.launch {
                val notificationsToSend = repo.getNotificationsToSend()
                for (notification in notificationsToSend) {
                    launch {
                        svc.sendNotification(notification)
                        repo.markAsSent(notification)
                    }
                }
            }
        }

        val notificationsToSend = List(20) { Notification(it) }
        val fakeRepo = FakeNotificationsRepo(notificationsToSend, 100L)
        val fakeService = FakeNotificationService(100L)
        val testScope = TestScope()

        codeToTest.invoke(fakeRepo,fakeService, testScope)
        testScope.advanceUntilIdle()

        // work was done
        assertEquals(notificationsToSend.toSet(), fakeService.sentNotifications.toSet())
        assertEquals(notificationsToSend.toSet(), fakeRepo.sentNotifications.toSet())

        // work was done concurrently
        assertEquals(300, testScope.currentTime)
    }

    /**
     * See [Pt2Ch2CoroutineContextAndScope.beforeClass] and [Pt2Ch2CoroutineContextAndScope.afterClass]
     */
    @Test
    fun `to test code using Dispatchers-Main, call Dispatchers-setMain`() {

    }

    private class MyViewModel(): ViewModel() {
        var progressBarVisible = false
        fun onCreate() {
            viewModelScope.launch {
                progressBarVisible = true
                delay(100)
                progressBarVisible = false
            }
        }

    }

    @Test
    fun `to test Android code using default ViewModelScope, call Dispatchers-setMain - it uses that`() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val viewModel = MyViewModel()

        viewModel.onCreate()
        assertEquals(false, viewModel.progressBarVisible)
        scheduler.advanceTimeBy(100)
        assertEquals(true, viewModel.progressBarVisible)
        scheduler.runCurrent()
        assertEquals(false, viewModel.progressBarVisible)
        Dispatchers.resetMain()
    }

    // prod problem simulation
    private class MyViewModel2(val dispatcher: CoroutineDispatcher): ViewModel() {
        var didWork = false
        fun doTheWork() {
            println("starting the work")
            viewModelScope.launch (dispatcher) {
                println("launch coroutine, delaying for 100")
                sleep(10)
                didWork = true
            }
        }
    }

    @Test
    fun `test ViewModel using Dispatchers-dot-IO`() = runTest {
        val testDispatcher = StandardTestDispatcher()
        val testScheduler = testDispatcher.scheduler

        val viewModel2 = MyViewModel2(testDispatcher)
        viewModel2.doTheWork()
        testScheduler.advanceUntilIdle()
        assertEquals(true, viewModel2.didWork)
    }


}