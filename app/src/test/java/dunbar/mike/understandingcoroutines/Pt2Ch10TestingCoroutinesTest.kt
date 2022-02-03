package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class Pt2Ch10TestingCoroutinesTest {
    interface Svc {
        suspend fun doThis()
        suspend fun doThat()
    }

    private suspend fun parallelizedSvcConsumer(svc: Svc) {
        val j1 = GlobalScope.launch {
            svc.doThis()
        }
        val j2 = GlobalScope.launch {
            svc.doThat()
        }
        j1.join()
        j2.join()
    }

    private suspend fun sequentialSvcConsumer(svc: Svc) {
        svc.doThis()
        svc.doThat()
    }

    @Test
    fun `test faked delays can be used to test timing perf, like parallelization vs sequential`() {
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
}