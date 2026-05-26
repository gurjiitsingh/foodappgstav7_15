package com.it10x.foodappgstav7_15

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

import com.it10x.foodappgstav7_15.data.PrinterPreferences
import com.it10x.foodappgstav7_15.data.online.repository.OrdersRepository
import com.it10x.foodappgstav7_15.printer.PrinterManager
import com.it10x.foodappgstav7_15.viewmodel.OnlineOrdersViewModel
import com.it10x.foodappgstav7_15.viewmodel.RealtimeOrdersViewModel
import com.it10x.foodappgstav7_15.navigation.NavigationHost
import com.it10x.foodappgstav7_15.printer.AutoPrintManager
import com.it10x.foodappgstav7_15.service.OrderListenerService

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import com.it10x.foodappgstav7_15.firebase.ClientIdStore
import com.it10x.foodappgstav7_15.ui.settings.ClientSetupScreen
import com.it10x.foodappgstav7_15.ui.theme.FoodPosTheme
import com.it10x.foodappgstav7_15.ui.theme.PosThemeMode

import com.it10x.foodappgstav7_15.viewmodel.ThemeViewModel

import com.google.firebase.firestore.FirebaseFirestore
import com.it10x.foodappgstav7_15.data.online.sync.GlobalOrderSyncManager
import com.it10x.foodappgstav7_15.data.pos.AppDatabaseProvider
import com.it10x.foodappgstav7_15.data.pos.KotProcessor

import androidx.activity.viewModels
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong

import androidx.compose.material.icons.filled.TableBar

import com.it10x.foodappgstav7_15.data.pos.repository.KotRepository
import com.it10x.foodappgstav7_15.data.pos.repository.POSOrdersRepository

import com.it10x.foodappgstav7_15.ui.kitchen.KitchenViewModel
import com.it10x.foodappgstav7_15.ui.kitchen.KitchenViewModelFactory

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.Alignment
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.it10x.foodappgstav7_15.core.FirstSyncManager
import com.it10x.foodappgstav7_15.core.PosRole
import com.it10x.foodappgstav7_15.core.PosRoleManager
import com.it10x.foodappgstav7_15.core.PosType

//import com.it10x.foodappgstav7_15.firebase.ClientRegistry
import com.it10x.foodappgstav7_15.core.rememberNetworkStatus
import com.it10x.foodappgstav7_15.data.pos.repository.PrinterRepository
import com.it10x.foodappgstav7_15.data.pos.repository.WaiterKitchenRepository
import com.it10x.foodappgstav7_15.data.printer.PrinterUploadManager
//import com.it10x.foodappgstav7_15.firebase.ClientFirebaseConfig
import com.it10x.foodappgstav7_15.data.model.ClientFirebaseConfig
import com.it10x.foodappgstav7_15.ui.cart.CartViewModel
import com.it10x.foodappgstav7_15.ui.settings.FirstAutoSyncScreen
import com.it10x.foodappgstav7_15.ui.waiterkitchen.WaiterKitchenViewModel
import com.it10x.foodappgstav7_15.ui.waiterkitchen.WaiterKitchenViewModelFactory
import com.it10x.foodappgstav7_15.network.RetrofitInstance
import com.it10x.foodappgstav7_15.ui.menu.fastfood.FastFoodMenu
import com.it10x.foodappgstav7_15.ui.menu.restaurant.RestaurantMainMenu
import com.it10x.foodappgstav7_15.ui.menu.restaurant.WaiterMenu
import com.it10x.foodappgstav7_15.ui.menu.retail.RetailMenu
import kotlin.math.log

class MainActivity : ComponentActivity() {
    private lateinit var globalOrderSyncManager: GlobalOrderSyncManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       // val role = PosRoleManager.getRole(this)
        val db = AppDatabaseProvider.get(this)

        //PRINTER SETTING UPLOAD
        // ================= PRINTER SYSTEM =================

// 1️⃣ Preferences
        val printerPreferences = PrinterPreferences(this)

// 2️⃣ Repository
        val printerRepository = PrinterRepository(
            db.printerDao()
        )

// 3️⃣ Upload manager
        val printerUploadManager by lazy {
            PrinterUploadManager(
                printerPreferences,
                printerRepository
            )
        }





        val processedDao = db.processedCloudOrderDao()
        val kotRepository = KotRepository(
            batchDao = db.kotBatchDao(),
            kotItemDao = db.kotItemDao(),
            tableDao = db.tableDao()
        )
        val printerManager = PrinterManager.getInstance(this)
        val kotProcessor = KotProcessor(
            kotItemDao = db.kotItemDao(),
            kotRepository = kotRepository,
            printerManager = printerManager
        )
        // 1️⃣ Get database instance


        val repository = POSOrdersRepository(
            db = db,
            orderMasterDao = db.orderMasterDao(),
            orderProductDao = db.orderProductDao(),
            cartDao = db.cartDao(),
            tableDao = db.tableDao(),
            virtualTableDao = db.virtualTableDao()
        )

        val tableId = "Bar_B_4"
        val tableName = "Bar B 4"
        val sessionId = "DINE_IN-Bar_B_4-1771777223369"
        val orderType = "DINE_IN"

        val kitchenFactory = KitchenViewModelFactory(
            app = application,
            tableId = tableId,
            tableName = tableName,
            sessionId = sessionId,
            orderType = orderType,
            repository = repository
        )


        val kitchenVM: KitchenViewModel by viewModels { kitchenFactory }






        setContent {

            val themeVM: ThemeViewModel = viewModel()
            val themeModeString by themeVM.themeMode.collectAsState()
            val themeMode = PosThemeMode.valueOf(themeModeString)
            val context = LocalContext.current

            val outletDao = db.outletDao()
            var posType by remember { mutableStateOf(PosType.RESTAU) }

            LaunchedEffect(Unit) {
                val outlet = outletDao.getOutlet()

                posType = try {
                    PosType.valueOf(outlet?.posType ?: "RESTAU")
                } catch (e: Exception) {
                    PosType.RESTAU
                }
            }



            var role by remember { mutableStateOf(PosRoleManager.getRole(context)) }
            FoodPosTheme(
                mode = themeMode
            ) {
                LaunchedEffect(role) {
                    if (role == null) {
                        PosRoleManager.saveRole(context, PosRole.MAIN)
                        role = PosRole.MAIN
                    }
                }
           // val clientId = remember { ClientIdStore.get(context) }
                var clientId by remember { mutableStateOf(ClientIdStore.get(context)) }
                if (clientId == null) {

                    ClientSetupScreen(
                        onActivated = {
                            clientId = ClientIdStore.get(context)
                        }
                    )

                    return@FoodPosTheme
                }



                var config by remember { mutableStateOf<ClientFirebaseConfig?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                // 🔥 LOAD LOCAL CONFIG (OFFLINE SUPPORT)
                val localConfig = ClientIdStore.getConfig(context)

                if (localConfig != null && config == null) {
                    config = ClientFirebaseConfig(
                        apiKey = localConfig.apiKey,
                        applicationId = localConfig.applicationId,
                        projectId = localConfig.projectId
                    )
                    isLoading = false
                }

                LaunchedEffect(clientId) {

                    // 🔥 Skip API if config already saved
                    if (ClientIdStore.getConfig(context) != null) {
                        isLoading = false
                        return@LaunchedEffect
                    }

                    try {
                        val response = RetrofitInstance.api.getClientConfig(clientId!!)

                        if (response.success) {
                            config = response.data

                            // 🔥 SAVE FOR FUTURE (ONE TIME ONLY)
                            ClientIdStore.saveConfig(
                                context,
                                response.data.apiKey,
                                response.data.applicationId,
                                response.data.projectId
                            )
                        } else {
                            error = "Invalid response"
                        }

                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        isLoading = false
                    }
                }

                if (isLoading) {
                    Text("Loading client config...")
                    return@FoodPosTheme
                }

                if (error != null) {
                    Text("Error: $error")
                    return@FoodPosTheme
                }

                if (config == null) {
                    Text("Config not found")
                    return@FoodPosTheme
                }

                FirebaseApp.getApps(context).forEach {
                    FirebaseApp.getInstance(it.name).delete()
                }

                val options = FirebaseOptions.Builder()
                    .setApiKey(config!!.apiKey)
                    .setApplicationId(config!!.applicationId)
                    .setProjectId(config!!.projectId)
                    .build()

                FirebaseApp.initializeApp(context, options)


                val firestore = remember { FirebaseFirestore.getInstance() }
                var firstSyncDone by remember {
                    mutableStateOf(FirstSyncManager.isFirstSyncDone(context))
                }

                if (!firstSyncDone) {

                    FirstAutoSyncScreen(
                        onFinished = {
                            firstSyncDone = true
                        }
                    )

                    return@FoodPosTheme
                }


                // ✅ Initialize Firebase dynamically now

                // Inside setContent { ... } in MainActivity

                val globalSyncManager = remember {
                    GlobalOrderSyncManager(
                        firestore = firestore,
                        processedDao = processedDao,
                        kitchenViewModel = kitchenVM,
                      //  waiterkitchenViewModel,
                        role = role ?: PosRole.MAIN,
                    )
                }

// Store globally in activity for later cleanup
                globalOrderSyncManager = globalSyncManager

// Start the appropriate listener based on role
                LaunchedEffect(globalSyncManager, role) {
                    // Always stop any previous listener first
                    globalSyncManager.stopListening()

                    // Start listener automatically according to role
                    globalSyncManager.startListening()
                }



            // ✅ START SERVICE ONLY NOW
                if (role == PosRole.MAIN) {
                    LaunchedEffect(Unit) {
                        val serviceIntent = Intent(context, OrderListenerService::class.java)
                        context.startForegroundService(serviceIntent)
                    }
                }
// ------------------------------------
// CORE SINGLETON OBJECTS (ONCE)
// ------------------------------------

            val ordersRepository = remember { OrdersRepository() }

            val ordersViewModel: OnlineOrdersViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return OnlineOrdersViewModel(printerManager) as T
                    }
                }
            )

            val autoPrintManager = remember {
                AutoPrintManager(
                    printerManager = printerManager,
                    ordersRepository = ordersRepository
                )
            }

            // ------------------------------------
            // REALTIME ORDERS VIEWMODEL (FACTORY)
            // ------------------------------------
            val realtimeOrdersVM: RealtimeOrdersViewModel =
                viewModel(factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return RealtimeOrdersViewModel(
                            application = application,
                            autoPrintManager = autoPrintManager
                        ) as T
                    }
                })



            // ------------------------------------
            // UI STATE
            // ------------------------------------
            val navController = rememberNavController()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {

                            // ✅ Make drawer scrollable
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 16.dp) // optional spacing at bottom
                            ) {
                                Log.d("postype", "posType: $posType")
                                // ===== HEADER =====
                                Text(
                                    "Menu",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(16.dp)
                                )


                                when (posType) {

                                    PosType.RETAIL -> RetailMenu(
                                        navController = navController,
                                        drawerState = drawerState,
                                        scope = scope
                                    )

                                    PosType.FAST_FOOD -> FastFoodMenu(
                                        navController = navController,
                                        drawerState = drawerState,
                                        scope = scope
                                    )

                                    PosType.RESTAU -> {
                                        when (role) {

                                            PosRole.MAIN -> RestaurantMainMenu(
                                                navController, drawerState, scope
                                            )

                                            PosRole.WAITER -> WaiterMenu(
                                                navController, drawerState, scope
                                            )

                                            else -> RestaurantMainMenu(   // ✅ fallback
                                                navController, drawerState, scope
                                            )
                                        }
                                    }

                                    else -> RestaurantMainMenu(
                                        navController = navController,
                                        drawerState = drawerState,
                                        scope = scope
                                    )
                                }


                            }
                        }
                    }
                ) {
                Scaffold(
                    topBar = {

                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val isOnline by rememberNetworkStatus()

                        CenterAlignedTopAppBar(

                            title = {},

                            navigationIcon = {

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    // Drawer button
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Menu"
                                        )
                                    }

                                    // Network indicator
                                    Text(
                                        text = if (isOnline) "🟢" else "🔴",
                                        modifier = Modifier.padding(start = 6.dp),
                                        color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },

                            actions = {

                                val commonShape = RoundedCornerShape(8.dp)
                                val commonHeight = 48.dp

                                if (role == PosRole.MAIN) {

                                    IconButton(
                                        onClick = {
                                            navController.navigate("tables") { launchSingleTop = true }
                                        },
                                        modifier = Modifier
                                            .size(commonHeight)
                                            .background(
                                                if (currentRoute == "tables")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                shape = commonShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TableBar,
                                            contentDescription = "Tables"
                                        )
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            navController.navigate("pos") { launchSingleTop = true }
                                        },
                                        modifier = Modifier
                                            .size(commonHeight)
                                            .background(
                                                if (currentRoute == "pos")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                shape = commonShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PointOfSale,
                                            contentDescription = "POS"
                                        )
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            navController.navigate("local_orders") { launchSingleTop = true }
                                        },
                                        modifier = Modifier
                                            .size(commonHeight)
                                            .background(
                                                if (currentRoute == "local_orders")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                shape = commonShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReceiptLong,
                                            contentDescription = "Orders"
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    StopSoundButton(viewModel = realtimeOrdersVM)
                                }

                                if (role == PosRole.WAITER) {

                                    IconButton(
                                        onClick = {
                                            navController.navigate("posWaiter") { launchSingleTop = true }
                                        },
                                        modifier = Modifier
                                            .size(commonHeight)
                                            .background(
                                                if (currentRoute == "posWaiter")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                shape = commonShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PointOfSale,
                                            contentDescription = "Waiter POS"
                                        )
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            navController.navigate("local_orders") { launchSingleTop = true }
                                        },
                                        modifier = Modifier
                                            .size(commonHeight)
                                            .background(
                                                if (currentRoute == "local_orders")
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                shape = commonShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReceiptLong,
                                            contentDescription = "Orders"
                                        )
                                    }
                                }
                            }
                        )

                    }

                ) { paddingValues ->

                    NavigationHost(
                        navController = navController,
                        printerManager = printerManager,
                        printerPreferences = printerPreferences,
                        printerUploadManager = printerUploadManager,
                        realtimeOrdersViewModel = realtimeOrdersVM,
                        paddingValues = paddingValues,
                        onSavePrinterSettings = { }
                    )
                }

            }
        }}
    }



    override fun onDestroy() {
        super.onDestroy()
        // Stop listener when activity is destroyed
        globalOrderSyncManager.stopListening()
    }

    @Composable
    fun StopSoundButton(viewModel: RealtimeOrdersViewModel) {

        val context = LocalContext.current
        val commonShape = RoundedCornerShape(8.dp)
        val commonHeight = 48.dp

        IconButton(
            onClick = {

                // 1️⃣ Stop ringtone in ViewModel
                viewModel.stopRingtone()

                // 2️⃣ Stop ringtone in Service
                val intent = Intent("STOP_RINGTONE")
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)
            },
            modifier = Modifier
                .size(commonHeight)
                .background(
                    MaterialTheme.colorScheme.error,
                    shape = commonShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.VolumeOff,
                contentDescription = "Stop Sound",
                tint = Color.White
            )
        }
    }

}

@Composable
fun SidebarSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)


            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
    Spacer(modifier = Modifier.height(4.dp)) // small separation
}



