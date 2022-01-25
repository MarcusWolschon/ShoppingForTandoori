package biz.wolschon.tandoorshopping.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biz.wolschon.tandoorshopping.common.model.Model
import biz.wolschon.tandoorshopping.common.page.*
import io.ktor.client.features.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private enum class TopLevelPages(val title: String, val page: Page) {
    LIST("Shopping List", ShoppingListPage()),
    FOODS("Foods", FoodsPage()),
    SHOPS("Shops", ShopsPage()),
    SETTINGS("⚙ Settings", SettingsPage())
}

@Composable
fun App(model: Model) {
    // state
    val platformContext = getPlatformContext()
    val errorMessage = model.errorMessage.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(TopLevelPages.LIST.page) }

    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    // pages
    Log.d("App", "App recomposing currentPage=${currentPage.title}")

    val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("App", "NetworkDispatcher got", throwable)
        //if (throwable is UnresolvedAddressException) {
        //model.baseUrl = null
        //}
        if (throwable is ClientRequestException) {
            model.settings.apiToken = null
        }
    }

    val refresh = {
        isRefreshing = true
        GlobalScope.launch(NetworkDispatcher + errorHandler) {
            Log.i("App", "refresh starting")
            try {
                model.fetchShoppingList()
                if (model.errorMessage.value == null) {
                    Log.i("App", "refresh - fetchFoods starting")
                    model.fetchFoods()
                    Log.i("App", "refresh done")
                }
                if (model.errorMessage.value == null) {
                    Log.i("App", "refresh - fetchSupermarkets starting")
                    model.fetchSupermarkets()
                    Log.i("App", "refresh done")
                }
            } finally {
                Log.i("App", "refresh finally")
                isRefreshing = false
            }
        }
    }

    if (model.settings.settingsIncomplete) {
        Log.i("App", "settings incomplete, forcing settings page")
        currentPage = TopLevelPages.SETTINGS.page
    } else if (model.databaseModel.getCachedShoppingListEntries().isEmpty() && errorMessage.value == null) {
        if (model.errorMessage.value == null && !isRefreshing) {
            Log.i("App", "initial refresh")
            refresh.invoke()
        }
    }



    Scaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            // Drawer header

            Text("Shopping for Tandoor", modifier = Modifier.padding(16.dp))

            Divider()

            // Drawer items

            TopLevelPages.values().forEach { menuEntry ->
                Button(
                    enabled = currentPage != menuEntry.page,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    onClick = {
                        Log.i("App", "[${menuEntry.title}] tapped")
                        currentPage = menuEntry.page
                        scope.launch { scaffoldState.drawerState.close() }
                    }) {
                    Text(menuEntry.title)
                }
            }

/*            Button(
                enabled = currentPage != TopLevelPages.LIST.page &&
                        model.databaseModel.getCachedShoppingListEntries().isNotEmpty(),
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                onClick = {
                    Log.i("App", "[list] tapped")
                    currentTopLevelPage = TopLevelPages.LIST
                    currentPage = currentTopLevelPage.page
                }) {
                Text(TopLevelPages.LIST.title)
            }

            Button(
                enabled = currentTopLevelPage != TopLevelPages.FOODS,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                onClick = {
                    Log.i("App", "[foods] tapped")
                    currentTopLevelPage = TopLevelPages.FOODS
                    currentPage = currentTopLevelPage.page
                }) {
                Text(TopLevelPages.FOODS.title)
            }

            Divider()

            Button(
                //always enabled //enabled = pageToShow != Pages.SETTINGS,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                onClick = {
                    Log.i("App", "[settings] tapped")
                    currentTopLevelPage = if (currentTopLevelPage == TopLevelPages.SETTINGS) TopLevelPages.LIST else TopLevelPages.SETTINGS
                    currentPage = currentTopLevelPage.page
                }
            ) {
                Text(TopLevelPages.SETTINGS.title)
            }*/
        },
        floatingActionButton = {
            if (!model.settings.settingsIncomplete && !isRefreshing) {
                ExtendedFloatingActionButton(
                    text = { Text("\uD83D\uDDD8 Refresh") },
                    onClick = {
                        Log.i("App", "[refresh] tapped")
                        refresh.invoke()
                    }

                )
            }
        }

    ) {


        Column {
            // top level bar
            Row {
                Button(
                    onClick = {
                        scope.launch {
                            scaffoldState.drawerState.apply {
                                if (isClosed) open() else close()
                            }
                        }
                    },
                    Modifier.height(48.dp).width(48.dp)
                ) {
                    Text("<")
                }

                if (isRefreshing) {
                    Row(Modifier.background(Color.Yellow).fillMaxWidth().height(48.dp),
                    ) {
                        Text(text = "loading...",
                            color = Color.Black,
                            modifier = Modifier
                                .align(CenterVertically)
                                .padding(horizontal = 16.dp, vertical = 0.dp)
                        )
                    }
                } else {
                    Text(text = currentPage.title,
                        color = Color.Black,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(CenterVertically)
                            .padding(horizontal = 16.dp, vertical = 0.dp)
                    )
                }
            }

            // error messages below level bar

            errorMessage.value?.let { error ->
                Row(Modifier.background(Color.Red).fillMaxWidth()) {
                    Text(text = error, color = Color.White, maxLines = 3)
                }
            }

            // main content area
            //TODO Column(Modifier.verticalScroll(contentAreaScrollState, true)) {

            currentPage.compose(model, platformContext) { destination ->
                currentPage = destination
            }
        }
    }
}


