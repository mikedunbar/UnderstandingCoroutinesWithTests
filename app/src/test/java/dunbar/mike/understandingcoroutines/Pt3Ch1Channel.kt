package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

@ExperimentalCoroutinesApi
class Pt3Ch1Channel {

    private val channel = Channel<Int>()

    @Test
    fun `Channel is an interface that implements 2 others - SendChannel and ReceiveChannel`() {
        assertTrue(channel is SendChannel<Int>)
        assertTrue(channel is ReceiveChannel<Int>)
    }

    @Test
    fun `Channel supports any number of senders and receivers`() = runTest {
        // see below
    }

    @Test
    fun `Every value that is sent is received only once`() = runTest {
        val allSent = Collections.synchronizedList(mutableListOf<Int>())
        val allReceived = Collections.synchronizedList(mutableListOf<Int>())

        val sender1 = launch {
            repeat(5) {
                delay(1000)
                val next = it + 2
                channel.send(next)
                allSent.add(next)
            }
        }

        val sender2 = launch {
            repeat(5) {
                delay(1000)
                val next = it + 20
                channel.send(next)
                allSent.add(next)
            }
        }

        val receiver1 = launch {
            repeat(5) {
                val received = channel.receive()
                allReceived.add(received)
            }
        }

        val receiver2 = launch {
            repeat(5) {
                val received = channel.receive()
                allReceived.add(received)
            }
        }
        sender1.join()
        sender2.join()
        receiver1.join()
        receiver2.join()
        assertEquals(allSent, allReceived)
    }

    @Test
    fun `Channels can specify a capacity, called a buffered channel - Channels without a set capacity are called rendezvous`() {
        val bufferedChannel = Channel<Int>(5)
    }

    @Test
    fun `ReceiveChannel-receive suspends until an element is available`() = runTest {
        var timeToReceive = 0L
        val receiver = launch {
            channel.receive()
            timeToReceive = currentTime
        }

        launch {
            delay(105)
            channel.send(1)
        }
        receiver.join()
        assertEquals(105, timeToReceive)
    }

    @Test
    fun `ReceiveChannel-tryReceive on a buffered channel does not suspend, but immediately returns ChannelResult`() =
        runTest {
            val bufferedChannel = Channel<Int>(2)
            var timeToTryReceive = 0L
            launch {
                bufferedChannel.tryReceive()
                timeToTryReceive = currentTime
            }

            val sender = launch {
                delay(105)
                bufferedChannel.send(1)
            }
            println("about to join")
            sender.join()
            assertEquals(0, timeToTryReceive)
        }

    @Test
    fun `SendChannel-send on a buffered channel suspends (by default) if the channel is at capacity`() {
        runTest {
            val bufferedChannel = Channel<Int>(1)
            var timeToSendAfterCapacity = 0L
            val sender = launch {
                bufferedChannel.send(1)
                bufferedChannel.send(2)
                timeToSendAfterCapacity = currentTime
            }
            launch {
                delay(105)
                bufferedChannel.receive()
            }

            println("about to join")
            sender.join()
            assertEquals(105, timeToSendAfterCapacity)
        }
    }

    @Test
    fun `SendChannel-send on a rendezvous channel suspends until received`() {

    }


    @Test
    fun `SendChannel-trySend on a buffered channel does not suspend, but immediately returns ChannelResult`() {
        runTest {
            val bufferedChannel = Channel<Int>(1)
            var timeToTrySendAfterCapacity = 0L
            val sender = launch {
                bufferedChannel.send(1)
                bufferedChannel.trySend(2)
                timeToTrySendAfterCapacity = currentTime
            }
            launch {
                delay(105)
                bufferedChannel.receive()
            }

            println("about to join")
            sender.join()
            assertEquals(0, timeToTrySendAfterCapacity)
        }
    }

    @Test
    fun `The produce function returns a ReceiveChannel that closes when the builder coroutine ends in any way - finished`() {
        runTest {
            val receiveChannel: ReceiveChannel<Int> = produce {
                send(1)
                send(2)
            }
            val received = mutableListOf<Int>()
            for (element in receiveChannel)
                received.add(element)
            assertEquals(listOf(1, 2), received)
            assertEquals(true, receiveChannel.isClosedForReceive)
        }
    }

    @Test
    fun `The produce function returns a ReceiveChannel that closes when the builder coroutine ends in any way - stopped`() {
    }

    @Test
    fun `The produce function returns a ReceiveChannel that closes when the builder coroutine ends in any way - cancelled`() {
    }

    @Test
    fun `There are 4 channel types, varying by capacity - Unlimited, Buffered, Rendezvous(default), Conflated`() {
        // see below
    }

    @Test
    fun `Channel-UNLIMITED has unlimited capacity and send never suspends`() = runTest {
        val testScope = this
        var productionTime = 0L
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel =
            produceReceiveChannel(testScope, Channel.UNLIMITED, produceList) { time: Long ->
                productionTime = time
            }

        var receiveList = listOf<Any>()
        consumeReceiveChannel(testScope, channel, 50) {
            println("list is $it")
            receiveList = it
        }

        assertEquals(produceList, receiveList)
        assertEquals(0, productionTime)
        assertEquals(150, currentTime)
    }

    @Test
    fun `Channel-BUFFERED has fixed capacity, defaulting to 64`() = runTest {
        val testScope = this
        var productionTime = 0L
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel = produceReceiveChannel(testScope, 1, produceList) { time: Long ->
            productionTime = time
        }

        var receiveList = listOf<Any>()
        consumeReceiveChannel(testScope, channel, 50) {
            println("list is $it")
            receiveList = it
        }

        assertEquals(produceList, receiveList)
        assertEquals(50, productionTime) // doesn't suspend until capacity+1, it seems
        assertEquals(150, currentTime)
    }

    @Test
    fun `Channel-RENDEZVOUS has zero capacity, so exchange only happens if sender and receiver meet`() =
        runTest {
            val testScope = this
            var productionTime = 0L
            val produceList = listOf("hello", "goodbye", "goodnight")
            val channel =
                produceReceiveChannel(testScope, Channel.RENDEZVOUS, produceList) { time: Long ->
                    productionTime = time
                }

            var receiveList = listOf<Any>()
            consumeReceiveChannel(testScope, channel, 50) {
                println("list is $it")
                receiveList = it
            }

            assertEquals(produceList, receiveList)
            assertEquals(100, productionTime)
            assertEquals(150, currentTime)
        }

    @Test
    fun `Channel-CONFLATED has capacity of 1, and each new element replaces the previous`() =
        runTest {
            val testScope = this
            var productionTime = 0L
            val produceList = listOf("hello", "goodbye", "goodnight")
            val channel =
                produceReceiveChannel(testScope, Channel.CONFLATED, produceList) { time: Long ->
                    productionTime = time
                }

            var receiveList = listOf<Any>()
            consumeReceiveChannel(testScope, channel, 50) {
                println("list is $it")
                receiveList = it
            }

            assertEquals(
                listOf("hello", "goodnight"),
                receiveList
            ) // elements are only replaced from the 2nd one on
            assertEquals(0, productionTime)
            assertEquals(100, currentTime)
        }

    @Test
    fun `onBufferOverflow = SUSPEND (default), makes send suspend when the buffer is full`() =
        runTest {
            val channel = Channel<String>(
                capacity = 1,
                onBufferOverflow = BufferOverflow.SUSPEND
            )

            val res1 = channel.trySend("one")
            val res2 = channel.trySend("two")
            assertEquals(true, res1.isSuccess)
            assertEquals(false, res2.isSuccess)
        }

    @Test
    fun `onBufferOverflow = DROP_OLDEST, drops oldest when the buffer is full`() = runTest {
        val testScope = this
        val produceList = listOf("hello", "goodbye", "goodnight", "one", "two", "three")
        val channel = Channel<String>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        for (element in produceList) {
            println("Sending $element")
            channel.send(element)
        }
        channel.close()


        var receiveList = listOf<Any>()
        consumeReceiveChannel(testScope, channel, 0) {
            println("list is $it")
            receiveList = it
        }

        assertEquals(listOf("three"), receiveList)
    }

    @Test
    fun `onBufferOverflow = DROP_LATEST, drops latest when the buffer is full`() = runTest {
        val testScope = this
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

        for (element in produceList) {
            println("Sending $element")
            channel.send(element)
        }
        channel.close()

        var receiveList = listOf<Any>()
        consumeReceiveChannel(testScope, channel, 0) {
            println("list is $it")
            receiveList = it
        }

        assertEquals(listOf("hello"), receiveList)
    }

    @Test
    fun `onUndeliveredElement is called when an element can't be delivered, and is typically used to close resources`() {
        var undelivered = "nothing"

        try {
            runTest {
                val onUndeliveredElement = { s: String ->
                    println("can't deliver that shit")
                    undelivered = s
                }

                val channel = Channel(capacity = 1, onUndeliveredElement = onUndeliveredElement)
                channel.send("hi")
                channel.close()
                println("sending after close")
                channel.send("something")
                println("done running test")
            }
        } catch (t: Throwable) {
            println("Caught that shit")
        }
        assertEquals("something", undelivered)
        println("done with test method")
    }

    @Test
    fun `Fan-out, multiple coroutines receiving from a single channel, distributes fairly`() =
        runTest {
            val testScope = this
            //produce 1-9 from single channel
            val numbers = produceReceiveChannel(this, Channel.UNLIMITED, (1..9).toList()) {}

            //consume from 3 coroutines, see it goes (1,4,7) , (2,5,8) , (3,6,9)
            var consumedListOne = listOf(-1)
            var consumedListTwo = listOf(-1)
            var consumedListThree = listOf(-1)

            launch { consumeInCoroutine(testScope, "one", numbers, 10) { consumedListOne = it } }
            launch { consumeInCoroutine(testScope, "two", numbers, 10) { consumedListTwo = it } }
            launch { consumeInCoroutine(testScope, "three", numbers, 10) { consumedListThree = it } }
            advanceUntilIdle()
            assertEquals(listOf(1, 4, 7), consumedListOne)
            assertEquals(listOf(2, 5, 8), consumedListTwo)
            assertEquals(listOf(3, 6, 9), consumedListThree)
        }

    private suspend fun consumeInCoroutine(
        testScope: TestScope,
        consumerId: String,
        numbers: ReceiveChannel<Int>,
        delayBeforeEachReceive: Long,
        consumedListCallback: (List<Int>) -> Unit
    ) {
        println("consumer $consumerId started at ${testScope.currentTime}")
        consumeReceiveChannel(
            testScope,
            numbers,
            delayBeforeEachReceive,
            consumerId,
            consumedListCallback
        )
    }

    @Test
    fun `Fan-in, multiple coroutines sending to a single channel, can be done with the produce function `() {
//        fun <T> CoroutineScope.fanIn(
//            channels: List<ReceiveChannel<T>>
//        ): ReceiveChannel<T> = produce {
//            for (channel in channels) {
//                launch {
//                    for (elem in channel) {
//                        send(elem)
//                    }
//                }
//            }
//        }
    }

    @Test
    fun `pipeline, one channel producing elements based on those received from another, ---`() {

        // numbers p1, squared p2
    }

    private fun <T> produceReceiveChannel(
        testScope: TestScope,
        capacity: Int,
        produceList: List<T>,
        delayBeforeEachSend: Long = 0,
        produceTimeCallback: (Long) -> Unit
    ) =
        testScope.produce(capacity = capacity) {
            val startTime = testScope.currentTime
            for (element in produceList) {
                delay(delayBeforeEachSend)
                send(element)
                println("element $element sent at ${testScope.currentTime}")
            }
            produceTimeCallback.invoke(testScope.currentTime - startTime)
        }

    private suspend fun <T> consumeReceiveChannel(
        scope: TestScope,
        channel: ReceiveChannel<T>,
        delayBeforeEachReceive: Long,
        id: String = "anon",
        consumedListCallback: (List<T>) -> Unit
    ) {
        val receiveList = mutableListOf<T>()
        for (element in channel) {
            delay(delayBeforeEachReceive)
            receiveList.add(element)
            println("element $element received by $id at ${scope.currentTime}")
        }
        consumedListCallback(receiveList)
    }
}