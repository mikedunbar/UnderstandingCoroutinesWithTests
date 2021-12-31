package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.coroutineContext

suspend fun main(): Unit = coroutineScope {
    val ints = Collections.synchronizedList(mutableListOf<Int>())
    val job = launch {
        repeat(10) { count ->
            Thread.sleep(10)
            ensureActive()
            ints.add(count)
        }
    }
    delay(41)
    job.cancelAndJoin()
    println("Ints: $ints")
}

