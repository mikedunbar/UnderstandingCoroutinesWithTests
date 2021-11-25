package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@DelicateCoroutinesApi
class JobTest {

    @Test
    fun `test launch returns a Job`() {
        val job: Job = GlobalScope.launch {}
    }

    @Test
    fun `test async returns Deferred which implements Job`() {
        val defferedJob = GlobalScope.async {}
        assertTrue(defferedJob is Job)
        assertTrue(defferedJob is Deferred)
    }

    @Test
    fun `test Jobs start active by default`() {
        val job = Job()
        assertTrue(job.isActive)

        val job2 = GlobalScope.launch {}
        assertTrue(job2.isActive)

        val job3 = GlobalScope.launch(start = CoroutineStart.DEFAULT) {}
        assertTrue(job3.isActive)
    }

    @Test
    fun `test Jobs can by started lazily`() {
        val job = GlobalScope.launch(start = CoroutineStart.LAZY) {

        }
        assertFalse(job.isActive)
        job.start()
        assertTrue(job.isActive)
    }

    @Test
    fun `test Jobs implement CoroutineContext and can be access from there`() {
        GlobalScope.launch {
            val job: Job? = coroutineContext[Job]
            assertNotNull(job)
            assertTrue(job is CoroutineContext)
        }
    }

    @Test
    fun `test Jobs can be access from CoroutineContext extension function job `() {
        GlobalScope.launch {
            val job: Job = coroutineContext.job
        }
    }

    @Test
    fun `test parent jobs have access to their child jobs`() {
        runBlocking {
            val parentJob = coroutineContext.job

            // remember - Deferred implements Job
            val childJob = async {}

            assertEquals(childJob, parentJob.children.first())
        }
    }

    @Test
    fun `test join waits for job to complete or cancel`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            val childJob = launch {
                delay(1000)
            }
            childJob.join()
        }
        assertTrue(System.currentTimeMillis() - startTime >= 1000)
    }

    @Test
    fun `test join can be used to wait for all children of a parent to complete `() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            launch {  delay(1000)}
            launch { delay(2000) }
            coroutineContext.job.children.forEach { it.join() }
        }
        assertTrue(System.currentTimeMillis() - startTime < 2000)


    }
}