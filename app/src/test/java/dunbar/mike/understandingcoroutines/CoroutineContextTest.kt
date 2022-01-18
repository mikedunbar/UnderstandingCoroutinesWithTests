package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

@DelicateCoroutinesApi
class CoroutineContextTest {

    @Test
    fun `test CoroutineName implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val name = CoroutineName("A name")
        assertTrue(name is CoroutineContext.Element && name is CoroutineContext)
    }

    @Test
    fun `test Job implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val job = Job()
        assertTrue(job is CoroutineContext.Element && job is CoroutineContext)
    }

    @Test
    fun `test SupervisorJob implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val bigBossMan = SupervisorJob()
        assertTrue(bigBossMan is CoroutineContext.Element && bigBossMan is CoroutineContext)
    }

    @Test
    fun `test CoroutineExceptionHandler implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val handler = CoroutineExceptionHandler { _, _ ->

        }
        assertTrue(handler is CoroutineContext.Element && handler is CoroutineContext)
    }

    //region From a later chapter
    @Test
    fun `test Dispatcher implements (some kind of) CoroutineContext interface`() {
        val customDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
        assertTrue(customDispatcher is CoroutineContext.Element)
        assertTrue(customDispatcher is CoroutineContext)
    }
    //endregion

    @Test
    fun `test Job starts in an active state`() {
        val job = Job()
        assertTrue(job.isActive)
    }

    @Test
    fun `test elements can be found in CoroutineContext using companion object key (shortcut) and get operator`() {
        val ctx: CoroutineContext = CoroutineName("Name")
        assertEquals("Name", ctx[CoroutineName]?.name)
    }

    @Test
    fun `test adding an elements with a different key to an existing context works`() {
        val ctx1 = CoroutineName("Name")
        val ctx2 = Job()
        val ctx3 = ctx1 + ctx2
        assertEquals(ctx1, ctx3[CoroutineName])
        assertEquals(ctx2, ctx3[Job])
    }

    @Test
    fun `test EmptyCoroutineContext returns null elements`() {
        val empty = EmptyCoroutineContext
        assertEquals(null, empty[CoroutineName])
        assertEquals(null, empty[Job])
        assertEquals(null, empty[CoroutineExceptionHandler])
    }

    @Test
    fun `test adding EmptyCoroutineContext doesn't modify existing CoroutineContext`() {
        val original: CoroutineContext = CoroutineName("ctx Name")
        val modified = original + EmptyCoroutineContext
        assertEquals(original, modified)
    }

    @Test
    fun `test minusKey removes elements from CoroutineContext`() {
        val ctx = CoroutineName("Name") + Job()
        val ctx2 = ctx.minusKey(CoroutineName)
        assertEquals(null, ctx2[CoroutineName])

        val ctx3 = ctx.minusKey(Job)
        assertEquals(null, ctx3[Job])
    }

    @Test
    fun `test fold function for accumulating over all elements in a CoroutineContext`() {
        val ctx = CoroutineName("Name") + Job()
        val folded = ctx.fold(" ") { accumulated, element -> "$accumulated$element " }
        assertTrue(folded.startsWith(" CoroutineName(Name) JobImpl{Active}@"))
    }

    @Test
    fun `test CoroutineContext available from CoroutineScope`() {
        runBlocking(CoroutineName("Name")) {
            assertEquals("Name", coroutineContext[CoroutineName]?.name)
        }
    }

    @Test
    fun `test CoroutineContext available from suspending functions too via coroutineContext`() {
        runBlocking {
            val name = async(CoroutineName("The Name")) {
                delay(100)
                nameFetcher()
            }
            assertEquals("The Name", name.await())
        }
    }

    private suspend fun nameFetcher(): String? {
        return coroutineContext[CoroutineName]?.name
    }

    @Test
    fun `test child inherits context from it's parent`() {
        runBlocking(CoroutineName("Parent")) {
            assertEquals("Parent", this.coroutineContext[CoroutineName]?.name)

            val childName = async {
                delay(100)
                this.coroutineContext[CoroutineName]?.name
            }

            assertEquals("Parent", childName.await())
        }
    }

    @Test
    fun `test child can override context from it's parent`() {
        runBlocking(CoroutineName("Parent")) {
            assertEquals("Parent", this.coroutineContext[CoroutineName]?.name)

            val childName = async(CoroutineName("Kiddo")) {
                delay(100)
                this.coroutineContext[CoroutineName]?.name
            }

            assertEquals("Kiddo", childName.await())
        }
    }

    @Test
    fun `test custom contexts obey inheritance and override rules as well`() {
        var parentValue: Int
        var childValueInherited: Int
        var childValueOverridden: Int
        runBlocking(MyCustomContext(5)) {
            parentValue = coroutineContext[MyCustomContext]?.value!!
            childValueInherited =
                withContext(Dispatchers.Default) {
                    coroutineContext[MyCustomContext]?.value!!
                }
            childValueOverridden = withContext(MyCustomContext(10)) {
                coroutineContext[MyCustomContext]?.value!!
            }
        }
        assertEquals(5, parentValue)
        assertEquals(5, childValueInherited)
        assertEquals(10, childValueOverridden)
    }

    class MyCustomContext(val value: Int) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> = Key

        companion object Key : CoroutineContext.Key<MyCustomContext>
    }
}