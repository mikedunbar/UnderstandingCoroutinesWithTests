package dunbar.mike.understandingcoroutines

import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemWithStateTest {

    data class User(val name: String)

    interface NetworkService {
        suspend fun fetchUser(id: Int): User
    }

    class UserFetcher(private val api: NetworkService) {
        private val users = mutableListOf<User>()

        fun usersFetched() = users.toList()

        suspend fun fetchUser(id: Int) {
            val newUser = api.fetchUser(id)
            users.add(newUser)
            println("user fetched is ${usersFetched().size} after fetching $id")
        }
    }

    class FakeNetworkService : NetworkService {
        override suspend fun fetchUser(id: Int): User {
            delay(2)
            return User("User$id")
        }
    }

    @DelicateCoroutinesApi
    @Test
    fun `test shared state issue`() {
        val fetcher = UserFetcher(FakeNetworkService())
        val runCount = 100_000
        runBlocking (Dispatchers.IO){
            coroutineScope {
                repeat(runCount) { count ->
                    launch {
                        fetcher.fetchUser(count)
                    }
                }
            }
        }
        println("usersAdded: ${fetcher.usersFetched().size}")
        assertTrue(fetcher.usersFetched().size < runCount)


    }
}