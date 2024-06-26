package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 *  (optional init)                    (default init)                                                  (final)
 *      [NEW]  ----------start---------> [ACTIVE] ---------complete-------> [COMPLETING] --finish--> [COMPLETED]
 *                           |                                |
 *                           |                                |
 *                       cancel/fail  ------------------------|
 *                           |
 *                           |
 *                           V                                                                         (final)
 *                        [CANCELLING]  ---------------------------- finish ---------------------->  [CANCELLED]
 *
 *
 *
 *
 */
@Suppress("USELESS_IS_CHECK")
@DelicateCoroutinesApi
class Pt2Ch3Job {

    @Test
    fun `Coroutines are represented by Jobs, launch returns a Job`() {
        assertTrue(GlobalScope.launch { } is Job)
    }

    @Test
    fun `async returns Deferred which implements Job`() {
        val deferredJob = GlobalScope.async {}
        assertTrue(deferredJob is Job)
        assertTrue(deferredJob is Deferred)
    }

    @Test
    fun `Jobs start active by default`() {
        val job = Job()
        assertTrue(job.isActive)

        val job2 = GlobalScope.launch {}
        assertTrue(job2.isActive)

        val job3 = GlobalScope.launch(start = CoroutineStart.DEFAULT) {}
        assertTrue(job3.isActive)

        val job4 = GlobalScope.async { }
        assertTrue(job4.isActive)

        val job5 = GlobalScope.async(start = CoroutineStart.DEFAULT) {}
        assertTrue(job5.isActive)
    }

    @Test
    fun `Jobs can by started lazily with start param`() {
        val job = GlobalScope.launch(start = CoroutineStart.LAZY) {}
        assertFalse(job.isActive)
        job.start()
        assertTrue(job.isActive)

        val deferredJob = GlobalScope.async(start = CoroutineStart.LAZY) { 5 }
        assertFalse(deferredJob.isActive)
        deferredJob.start()
        assertTrue(deferredJob.isActive)
    }

    @Test
    fun `complete moves a Job to Completed state`() {
        val job = Job()
        assertTrue(job.isActive)
        job.complete()
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
        assertFalse(job.isActive)
    }

    @Test
    fun `cancel moves a Job to Cancelled state`() {
        val job = Job()
        job.cancel("you're done")
        assertTrue(job.isCancelled)
        assertTrue(job.isCompleted)
        assertFalse(job.isActive)
    }

    @Test
    fun `parent Job moves to Completed when all it's children complete`() {
        GlobalScope.launch {
            val parentJob = coroutineContext.job
            val child1 = async { delay(1000); 5 }
            val child2 = async { delay(500); 3 }
            assertTrue(parentJob.isActive)
            assertEquals(8, child1.await() + child2.await())
            assertTrue(parentJob.isCompleted)
        }
    }

    @Test
    fun `Job implements CoroutineContext and can be access from there`() {
        GlobalScope.launch {
            val job: Job? = coroutineContext[Job]
            assertNotNull(job)
            assertTrue(job is CoroutineContext)
            assertTrue(job!!.isActive)
        }
    }

    @Test
    fun `Job can be accessed from CoroutineContext's job extension function`() {
        GlobalScope.launch {
            val job: Job = coroutineContext.job
            assertNotNull(job)
            assertTrue(job is CoroutineContext)
        }
    }

    @Test
    fun `Job, unlike other CoroutineContext elements, is NOT passed unchanged from parent to child`() =
        runBlocking(CoroutineName("parent name")) {
            val parentName = coroutineContext[CoroutineName]!!.name
            val parentJob = coroutineContext[Job]

            var childName = ""
            val childJob = launch {
                childName = coroutineContext[CoroutineName]!!.name
            }

            childJob.join()
            assertFalse(parentJob == childJob)
            assertEquals(parentName, childName)
        }

    @Test
    fun `parent job provides access to child jobs`() {
        runBlocking {
            val parentJob = coroutineContext.job

            // remember - Deferred implements Job
            val childJob = async {}

            assertEquals(childJob, parentJob.children.first())
        }
    }

    @Test
    fun `join waits for job to complete or cancel`() {
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
    fun `Job() factory function fake constructor returns a CompletableJob`() {
        val job = Job()
        assertTrue(job is CompletableJob)
    }

    @Test
    fun `joining on a CompletableJob never completes, unless complete or completeExceptionally are called`() {
        runBlocking {
            val job = Job()
            launch(job) {}
            launch(job) {}
            job.complete()  // comment out this line, to see this run forever
            job.join()
        }
    }

    @Test
    fun `join can be used to wait for all children of a parent to complete `() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            val job = Job()
            launch(job) { delay(1000) }
            launch(job) { delay(2000) }
            job.children.forEach { it.join() }
        }
        assertTrue(System.currentTimeMillis() - startTime > 2000)
    }

    @Test
    fun `CompletableJob-dot-complete waits for children to complete`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            val job = Job()
            launch(job) { delay(1000) }
            launch(job) { delay(2000) }
            job.complete()
            job.join()
        }
        assertTrue(System.currentTimeMillis() - startTime > 2000)
    }

    @Test
    fun `CompletableJob-dot-completeExceptionally does NOT wait for children to complete`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            val job = Job()
            launch(job) { delay(1000) }
            launch(job) { delay(2000) }
            val exception = Exception("Shit went wrong")
            job.completeExceptionally(exception)
            job.join()
        }
        assertTrue(System.currentTimeMillis() - startTime < 1000)
    }

    @Test
    fun `Job factory function accepts a parent, where structured concurrency applies`() {
        val startTime = System.currentTimeMillis()
        runBlocking {
            val parentJob = Job()
            val job = Job(parentJob)
            launch(job) { delay(1000) }
            launch(job) { delay(2000) }
            parentJob.cancel()
            job.children.forEach { it.join() }
        }
        assertTrue(System.currentTimeMillis() - startTime < 1000)
    }
}