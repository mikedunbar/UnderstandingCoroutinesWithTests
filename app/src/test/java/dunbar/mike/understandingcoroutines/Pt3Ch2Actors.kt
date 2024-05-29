package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Actors are computational entities that, in response to a receive message, can concurrently
 *  - send a finite number of messages to other actors
 *  - create a finite number of new actors
 *  - designate the the behavior for the next message it receives
 *
 *  Actors can only directly modify their own private state
 *  Actors don't need synchronization, because each runs on a single thread and handles messages on at a time
 */
@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
class Pt3Ch2Actors {

    @Test
    fun `actor coroutine builder supports this model`() = runTest {
        val counterActor = counterActor()
        massiveRun { counterActor.send(IncCounter) }
        val response = CompletableDeferred<Int>()
        counterActor.send(GetCounter(response))
        val counted = response.await()
        assertEquals(10_000, counted)
        println("counted $counted after $currentTime")
        counterActor.close()
    }

    @Test
    fun `an exception in the builder closes the channel`() {

    }

    sealed class CounterMsg

    object IncCounter : CounterMsg()

    class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg()

    private fun CoroutineScope.counterActor() = actor<CounterMsg> {
        var counter = 0
        for (msg in channel) {
            when (msg) {
                is IncCounter -> counter++
                is GetCounter -> msg.response.complete(counter)
            }
        }
    }

    private suspend fun massiveRun(block: suspend () -> Unit) = coroutineScope {
        repeat(10_000) { launch { block() } }
    }
}