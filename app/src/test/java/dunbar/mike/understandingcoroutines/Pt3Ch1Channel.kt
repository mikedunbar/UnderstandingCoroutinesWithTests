package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
    fun `SendChannel-send on a buffered channel suspends if the channel is at capacity`() {
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
    fun `The produce function returns a ReceiveChannel that closes when the builder coroutine ends in any way - stopped`() {}

    @Test
    fun `The produce function returns a ReceiveChannel that closes when the builder coroutine ends in any way - cancelled`() {}

    @Test
    fun `There are 4 channel types, varying by capacity - Unlimited, Buffered, Rendezvous(default), Conflated`() {
        // see below
    }

    @Test
    fun `Channel-UNLIMITED has unlimited capacity and send never suspends`() = runTest {
        val testScope = this
        var productionTime = 0L
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel = produceReceiveChannel(testScope, Channel.UNLIMITED, produceList) { time: Long ->
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
    fun `Channel-RENDEZVOUS has zero capacity, so exchange only happens if sender and receiver meet`() = runTest {
        val testScope = this
        var productionTime = 0L
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel = produceReceiveChannel(testScope, Channel.RENDEZVOUS, produceList) { time: Long ->
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
    fun `Channel-CONFLATED has capacity of 1, and each new element replaces the previous`() = runTest {
        val testScope = this
        var productionTime = 0L
        val produceList = listOf("hello", "goodbye", "goodnight")
        val channel = produceReceiveChannel(testScope, Channel.CONFLATED, produceList) { time: Long ->
            productionTime = time
        }

        var receiveList = listOf<Any>()
        consumeReceiveChannel(testScope, channel, 50) {
            println("list is $it")
            receiveList = it
        }

        assertEquals(listOf("hello", "goodnight"), receiveList) // elements are only replaced from the 2nd one on
        assertEquals(0, productionTime)
        assertEquals(100, currentTime)
    }

    private fun produceReceiveChannel(
        testScope: TestScope,
        capacity: Int,
        produceList: List<String>,
        produceTimeCallback: (Long) -> Unit
    ) =
        testScope.produce(capacity = capacity) {
            val startTime = testScope.currentTime
            for (element in produceList) {
                send(element)
                println("element $element sent at ${testScope.currentTime}")
            }
            produceTimeCallback.invoke(testScope.currentTime - startTime)
        }

    private suspend fun consumeReceiveChannel(
        scope: TestScope,
        channel: ReceiveChannel<Any>,
        delayAfterEachReceive: Long,
        consumedListCallback: (List<Any>) -> Unit
    ) {
        val receiveList = mutableListOf<Any>()
        for (element in channel) {
            println("element received at ${scope.currentTime}")
            receiveList.add(element)
            delay(delayAfterEachReceive)
        }
        consumedListCallback(receiveList)
    }
}