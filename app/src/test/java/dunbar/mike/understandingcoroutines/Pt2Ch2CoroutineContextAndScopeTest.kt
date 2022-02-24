package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
@ExperimentalStdlibApi
class Pt2Ch2CoroutineContextAndScopeTest {

    @Test
    fun `CoroutineName implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val name = CoroutineName("A name")
        assertTrue(name is CoroutineContext.Element && name is CoroutineContext)
    }

    @Test
    fun `Job implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val job = Job()
        assertTrue(job is CoroutineContext.Element && job is CoroutineContext)
    }

    @Test
    fun `SupervisorJob implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val bigBossMan = SupervisorJob()
        assertTrue(bigBossMan is CoroutineContext.Element && bigBossMan is CoroutineContext)
    }

    @Test
    fun `CoroutineExceptionHandler implements CoroutineContext-dot-Element, which implements CoroutineContext`() {
        val handler = CoroutineExceptionHandler { _, _ ->

        }
        assertTrue(handler is CoroutineContext.Element && handler is CoroutineContext)
    }

    //region From a later chapter
    @Test
    fun `Dispatcher implements (some kind of) CoroutineContext interface`() {
        val customDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
        assertTrue(customDispatcher is CoroutineContext.Element)
        assertTrue(customDispatcher is CoroutineContext)
    }
    //endregion

    @Test
    fun `elements can be found in CoroutineContext using companion object key as index`() {
        val ctx: CoroutineContext = CoroutineName("Name")
        assertEquals("Name", ctx[CoroutineName]?.name)
    }

    @Test
    fun `contexts can be added together, and the result contains all elements from both`() {
        val ctx1 = CoroutineName("Name")
        val ctx2 = Job()
        val ctx3 = ctx1 + ctx2
        assertEquals(ctx1, ctx3[CoroutineName])
        assertEquals(ctx2, ctx3[Job])
    }

    @Test
    fun `EmptyCoroutineContext returns null elements`() {
        val empty = EmptyCoroutineContext
        assertEquals(null, empty[CoroutineName])
        assertEquals(null, empty[Job])
        assertEquals(null, empty[CoroutineExceptionHandler])
    }

    @Test
    fun `adding EmptyCoroutineContext doesn't modify existing CoroutineContext`() {
        val original: CoroutineContext = CoroutineName("ctx Name")
        val modified = original + EmptyCoroutineContext
        assertEquals(original, modified)
    }

    @Test
    fun `minusKey function removes elements from CoroutineContext`() {
        val ctx = CoroutineName("Name") + Job()
        val ctx2 = ctx.minusKey(CoroutineName)
        assertEquals(null, ctx2[CoroutineName])

        val ctx3 = ctx.minusKey(Job)
        assertEquals(null, ctx3[Job])
    }

    @Test
    fun `fold function for accumulating over all elements in a CoroutineContext`() {
        val ctx = CoroutineName("Name") + Job()
        val folded = ctx.fold(" ") { accumulated, element -> "$accumulated$element " }
        assertTrue(folded.startsWith(" CoroutineName(Name) JobImpl{Active}@"))
    }

    @Test
    fun `CoroutineContext available from CoroutineScope`() {
        runBlocking(CoroutineName("Name")) {
            val scope = this
            assertEquals("Name", scope.coroutineContext[CoroutineName]?.name)
        }
    }

    @Test
    fun `CoroutineContext available from suspending functions too via coroutineContext`() {
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
    fun `child inherits context from it's parent`() {
        runBlocking(CoroutineName("Parent")) {
            assertEquals("Parent", this.coroutineContext[CoroutineName]?.name)

            val childName = async {
                this.coroutineContext[CoroutineName]?.name
            }

            assertEquals("Parent", childName.await())
        }
    }

    @Test
    fun `child can override context from it's parent`() {
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
    fun `custom contexts obey inheritance and override rules as well`() {
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

    private class MyCustomContext(val value: Int) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> = Key

        companion object Key : CoroutineContext.Key<MyCustomContext>
    }

    @Test
    fun `CoroutineScope is just a reference to a CoroutineContext`() {
        GlobalScope.async {
            val scope = this
            assertTrue(scope.coroutineContext is CoroutineContext)
        }
    }

    @Test
    fun `Scope and Context only differ in their intended purpose`() {

    }

    @Test
    fun `Scopes are used to launch coroutines, and take a Context as a parameter - the 2 contexts are merged`() =
        runBlocking {
            val mainScope = MainScope()
            val mainScopeName = mainScope.coroutineContext[CoroutineName]
            val mainScopeDispatcher = mainScope.coroutineContext[CoroutineDispatcher]
            var launchedScopeName = ""
            var launchedScopeDispatcher: CoroutineDispatcher? = null
            mainScope.launch(CoroutineName("Context Name")) {
                launchedScopeName = coroutineContext[CoroutineName]?.name ?: "fail"
                launchedScopeDispatcher = coroutineContext[CoroutineDispatcher]
            }.join()
            assertEquals(mainScopeDispatcher, launchedScopeDispatcher)
            assertEquals("Context Name", launchedScopeName)
            assertEquals(null, mainScopeName)
        }

    @Test
    fun `elements in the Context parameter take precedence over those in the Scope`() =
        runBlocking {
            val mainScope = MainScope()
            val mainScopeDispatcher = mainScope.coroutineContext[CoroutineDispatcher]
            var launchedScopeDispatcher: CoroutineDispatcher? = null
            mainScope.launch(Dispatchers.IO) {
                launchedScopeDispatcher = coroutineContext[CoroutineDispatcher]
            }.join()
            assertEquals(Dispatchers.Main, mainScopeDispatcher)
            assertEquals(Dispatchers.IO, launchedScopeDispatcher)
        }

    @Test
    fun `Nested Scope-dot-launch coroutine isn't a child of containing Scope-dot-launch coroutine`() =
        runBlocking {
            var innerJob1: Job? = null
            var innerJob2: Job? = null
            val job1 = MainScope().launch {
                innerJob1 = MainScope().launch {
                    delay(100)
                }
            }

            val job2 = MainScope().launch {
                innerJob2 = launch {
                    delay(100)
                }
            }
            assertFalse(job1.children.contains(innerJob1))
            assertTrue(job2.children.contains(innerJob2))
        }

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            Dispatchers.setMain(Dispatchers.Default)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            Dispatchers.resetMain()
        }
    }

}