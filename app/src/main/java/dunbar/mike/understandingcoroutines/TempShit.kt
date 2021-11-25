package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

suspend fun main() = withContext(CoroutineName("Outer")) {
    printName()
    launch(CoroutineName("Inner")) {
        printName()
    }
    delay(10)
    printName()
}

suspend fun printName() {
    println(coroutineContext[CoroutineName]?.name)
}
