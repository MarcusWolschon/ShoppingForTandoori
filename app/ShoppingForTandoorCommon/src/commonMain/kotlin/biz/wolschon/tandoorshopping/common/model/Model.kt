package biz.wolschon.tandoorshopping.common.model

import biz.wolschon.tandoorshopping.common.model.db.AppDatabase
import biz.wolschon.tandoorshopping.common.DatabaseDriverFactory
import biz.wolschon.tandoorshopping.common.api.APIClient
import biz.wolschon.tandoorshopping.common.DBDispatcher
import biz.wolschon.tandoorshopping.common.Log
import biz.wolschon.tandoorshopping.common.api.model.TandoorError
import biz.wolschon.tandoorshopping.common.api.model.TandoorFood
import biz.wolschon.tandoorshopping.common.api.model.TandoorShoppingList
import biz.wolschon.tandoorshopping.common.api.model.TandoorShoppingListEntry
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import io.ktor.client.features.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.channels.UnresolvedAddressException

class Model(dbDriver: DatabaseDriverFactory) {
    companion object {
        const val defaultApiURL = "https://rezepte.SERVER/api"
    }

    val settingsIncomplete
        get() = apiToken.isNullOrBlank() || apiUrl == defaultApiURL
    private val api = APIClient()
    private val database = AppDatabase(
        dbDriver.createDriver()
        //XYZAdapter = XYZ.Adapter(OtherAdapters,...)
    )
    val errorMessage = MutableStateFlow<String?>(null)

    val apiUrlLive: Flow<String> = database.settingsQueries.getSetting("apiUrl").asFlow().mapToOneOrNull(context = DBDispatcher).map { it?.value ?: defaultApiURL }
    var apiUrl: String?
        get() = database.settingsQueries.getSetting("apiUrl").executeAsOneOrNull()?.value?.trim() ?: defaultApiURL
        set(value) = database.settingsQueries.replaceSetting("apiUrl", value)

    val apiTokenLive: Flow<String> = database.settingsQueries.getSetting("apiToken").asFlow().mapToOneOrNull(context = DBDispatcher).map { it?.value ?: "" }
    var apiToken: String?
        get() = database.settingsQueries.getSetting("apiToken").executeAsOneOrNull()?.value
        set(value) = database.settingsQueries.replaceSetting("apiToken", value)

    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("Model", "Networking error: ", throwable)
        if (throwable is UnresolvedAddressException) {
            errorMessage.value = "Server address unresolvable"
        }
        if (throwable is ClientRequestException) {
            errorMessage.value = throwable.message
        }
    }

    private val errorJson = Json(KotlinxSerializer.DefaultJson) {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchFoods(): List<TandoorFood>? {
        val apiUrl = apiUrl ?: return null
        val apiToken = apiToken ?: return null
        try {
            return coroutineScope {
                var response = api.fetchFoods(apiUrl, apiToken)
                val allFoods = response.results.toMutableList()
                while (response.next != null) {
                    response = api.fetchMoreFoods(response.next ?: break, apiToken)
                    allFoods.addAll(response.results)
                }

                allFoods
            }
        } catch (x: UnresolvedAddressException) {
            Log.e("Model", "fetchFoods() error", x)
            errorMessage.value = "Server address unresolvable"
        } catch (x: ClientRequestException) {
            Log.e("Model", "fetchFoods() error", x)
            handleClientRequestException(x)
        }
        return null
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleClientRequestException(x: ClientRequestException) {
        val plainText = x.response.readText(Charsets.UTF_8)
        try {
            val message = errorJson.decodeFromString<TandoorError>(plainText)
            errorMessage.value = "Error: ${message.detail}"
        } catch (ex: Exception) {
            Log.e("Model", "Can't parse error response $plainText", ex)
            errorMessage.value = "Error: $plainText"
        }
    }

    suspend fun fetchShoppingLists(): List<TandoorShoppingList>? {
        val apiUrl = apiUrl ?: return null
        val apiToken = apiToken ?: return null
        try {
            return coroutineScope { api.fetchShoppingLists(apiUrl, apiToken) }
        } catch (x: UnresolvedAddressException) {
            Log.e("Model", "fetchShoppingLists() error", x)
            errorMessage.value = "Server address unresolvable"
        } catch (x: ClientRequestException) {
            Log.e("Model", "fetchShoppingLists() error", x)
            handleClientRequestException(x)
        }
        return null
    }

    suspend fun updateShoppingListItemChecked(id: Int, checked: Boolean) {
        val apiUrl = apiUrl ?: return
        val apiToken = apiToken ?: return
        return withContext(errorHandler) {
            coroutineScope {
                api.updateShoppingListItemChecked(apiUrl, apiToken, id, checked)
            }
        }
    }

    suspend fun updateShoppingListItemChecked(entry: TandoorShoppingListEntry) {
        val apiUrl = apiUrl ?: return
        val apiToken = apiToken ?: return
        return withContext(errorHandler) {
            api.updateShoppingListItemChecked(apiUrl, apiToken, entry)
        }
    }
}