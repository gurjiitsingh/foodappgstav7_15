package com.it10x.foodappgstav7_15.ui.kitchen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Transaction
import com.google.firebase.firestore.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.it10x.foodappgstav7_15.data.PrinterRole
import com.it10x.foodappgstav7_15.data.online.sync.TableKotSyncService
import com.it10x.foodappgstav7_15.data.pos.AppDatabaseProvider
import com.it10x.foodappgstav7_15.data.pos.entities.PosCartEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosKotBatchEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosKotItemEntity
import com.it10x.foodappgstav7_15.data.pos.repository.CartRepository
import com.it10x.foodappgstav7_15.data.pos.repository.POSOrdersRepository
import com.it10x.foodappgstav7_15.data.pos.usecase.KotToBillUseCase
import com.it10x.foodappgstav7_15.printer.PrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.it10x.foodappgstav7_15.data.pos.repository.KotRepository
import com.it10x.foodappgstav7_15.data.pos.repository.VirtualTableRepository
import com.it10x.foodappgstav7_15.data.pos.manager.TableSyncManager
import com.it10x.foodappgstav7_15.printer.ReceiptFormatter
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.flow.asStateFlow

class KitchenViewModel(
    app: Application,
    private val tableId: String,
    private val tableName: String,
    private val sessionId: String,
    private val orderType: String,
    private val repository: POSOrdersRepository,

    ) : AndroidViewModel(app) {

    //  var isFromFirestore = false
    private val firestore = FirebaseFirestore.getInstance()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> get() = _loading
    private val kotItemDao =
        AppDatabaseProvider.get(app).kotItemDao()


    private val kotToBillUseCase =
        KotToBillUseCase(kotItemDao)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val kotItems: StateFlow<List<PosKotItemEntity>> =
        kotItemDao.getAllKotItems()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )


    private val kotRepository = KotRepository(
        AppDatabaseProvider.get(app).kotBatchDao(),
        AppDatabaseProvider.get(app).kotItemDao(),
        AppDatabaseProvider.get(app).tableDao()
    )

    private val cartRepository = CartRepository(
        AppDatabaseProvider.get(app).cartDao(),
        AppDatabaseProvider.get(app).tableDao()
    )

    private val virtualTableRepository = VirtualTableRepository(
        AppDatabaseProvider.get(app).virtualTableDao(),
        AppDatabaseProvider.get(app).cartDao(),
        AppDatabaseProvider.get(app).kotItemDao()
    )

    private val tableSyncManager = TableSyncManager(
        tableRepo = kotRepository,
        cartRepo = cartRepository,
        virtualRepo = virtualTableRepository
    )

    val printerManager =
        PrinterManager.getInstance(getApplication<Application>().applicationContext)


    private val tableKotSyncService = TableKotSyncService(
        firestore,
        kotItemDao
    )


    fun getPendingItems(orderRef: String, orderType: String): Flow<List<PosKotItemEntity>> {


        return if (orderType == "DINE_IN" || orderType == "TAKEAWAY" || orderType == "DELIVERY") {
            kotItemDao.getPendingItemsForTable(orderRef)
        } else {
            kotItemDao.getPendingItemsForTable(orderType)
            //  kotItemDao.getPendingItemsForSession(orderRef)
        }
    }

    // THIS FUNCTION RECEIVE ITEM FROM MAIN POS
    fun cartToKotMainPOS1(
        orderType: String,
        tableNo: String,
        sessionId: String,
        paymentType: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String?,
        role: String,
    ) {
        viewModelScope.launch {

            if (_isSending.value) return@launch   // ✅ prevent double trigger

            _isSending.value = true
            _loading.value = true

            val sessionKey = sessionId
            val tableId = tableNo   // ❌ removed !! (no crash risk)

            val cartList = repository.getCartItemsByTableId(tableId).first()

            if (cartList.isEmpty()) {
                Log.w(
                    "KITCHEN_DEBUG4",
                    "⚠️ No new items found for orderType=$orderType (sessionKey=$sessionKey)"
                )
                _isSending.value = false
                _loading.value = false
                return@launch
            }

            try {
                val kotSaved = saveKotFromMainPOSandWairterFirestore(
                    orderType = orderType,
                    sessionId = sessionId,
                    tableNo = tableNo,
                    cartItems = cartList,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    appVersion = appVersion,
                    role = role,
                    source = "POS",
                )

                if (!kotSaved) {
                    Log.e("KITCHEN_DEBUG4", "saveKotOnly() failed for session=$sessionKey")
                    return@launch
                }
                launch(Dispatchers.IO) {
                repository.clearCart(orderType, tableId)
                    tableSyncManager.syncCart(tableId, orderType)
                    tableSyncManager.syncBill(tableId, orderType)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSending.value = false
                _loading.value = false
            }
        }
    }


    fun cartToKotMainPOS(
        orderType: String,
        tableNo: String,
        sessionId: String,
        paymentType: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String?,
        role: String,
    ) {
        viewModelScope.launch {

            if (_isSending.value) return@launch

            _isSending.value = true
            _loading.value = true

            val tableId = tableNo

            val cartList = repository.getCartItemsByTableId(tableId).first()

            if (cartList.isEmpty()) {
                _isSending.value = false
                _loading.value = false
                return@launch
            }

            try {
                val kotSaved = withContext(Dispatchers.IO) {
                    saveKotFromMainPOSandWairterFirestore(
                        orderType = orderType,
                        sessionId = sessionId,
                        tableNo = tableNo,
                        cartItems = cartList,
                        deviceId = deviceId,
                        deviceName = deviceName,
                        appVersion = appVersion,
                        role = role,
                        source = "POS",
                    )
                }

                if (!kotSaved) {
                    return@launch
                }

            } finally {
                _isSending.value = false
                _loading.value = false
            }

            // ✅ background work AFTER UI state is reset
            launch(Dispatchers.IO) {
                try {
                    repository.clearCart(orderType, tableId)
                    tableSyncManager.syncCart(tableId, orderType)
                    tableSyncManager.syncBill(tableId, orderType)
                } catch (e: Exception) {
                    Log.e("SYNC", "Background sync failed", e)
                }
            }
        }
    }

    //THIS FUNCTION RECEIVE ITEM FORM WAITER THROUGH FIRESTORE
    suspend fun saveKotFromFirestoreWaiter(
        orderType: String,
        sessionId: String,
        tableNo: String,
        cartItems: List<PosCartEntity>,
        deviceId: String,
        deviceName: String?,
        appVersion: String?,
        role: String,
        source: String,
    ) {
        //THIS FUNCTION RECEIVE DATA FROM WAITER POS 1.
        if (cartItems.isEmpty()) {
            Log.w("KOT_BRIDGE", "⚠️ createKotAndPrint called with empty cartItems")
            return
        }

        _loading.value = true

        try {
            val saved = saveKotFromMainPOSandWairterFirestore(
                orderType = orderType,
                sessionId = sessionId,
                tableNo = tableNo,
                cartItems = cartItems,
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                role = role,
                source = source,
            )

            if (!saved) {
                Log.e("KOT_BRIDGE", "❌ Failed to create KOT + Print")
                return
            }

            kotRepository.syncBillCount(tableNo)
        } catch (e: Exception) {
            Log.e("KOT_BRIDGE", "❌ Exception in createKotAndPrint()", e)
        } finally {
            _loading.value = false
        }
    }


// PRIVATE USED BY MAIN POS

    private suspend fun saveKotFromMainPOSandWairterFirestore(
        orderType: String,
        sessionId: String,
        tableNo: String?,
        cartItems: List<PosCartEntity>,
        deviceId: String,
        deviceName: String?,
        appVersion: String?,
        role: String,
        source: String,
    ): Boolean = withContext(Dispatchers.IO) {
        //  Log.d("KOT", "saveKotAndPrintKitchen Called from: ${Throwable().stackTrace[1]}")
        //FROM MAIN POS AND
        //FROM FIRESTORE WAITER POS
        val tableNo = tableNo ?: "";
        try {
            val db = AppDatabaseProvider.get(printerManager.appContext())
            val kotBatchDao = db.kotBatchDao()
            val kotItemDao = db.kotItemDao()

            val batchId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val batch = PosKotBatchEntity(
                id = batchId,
                sessionId = sessionId,
                tableNo = tableNo,
                orderType = orderType,
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                createdAt = now,
                sentBy = null,
                syncStatus = "DONE",
                lastSyncedAt = null
            )

            kotBatchDao.insert(batch)

            val items = cartItems.map { cart ->
              //  Log.d("ORDER_TYPE_TRACE", "orderType=$orderType  tableNo=${tableNo}")

                val modifierTotal = ModifierJsonHelper
                    .fromJson(cart.modifiersJson)
                    .sumOf { group -> group.items.sumOf { it.price } }

                val finalPrice = cart.basePrice + modifierTotal

                PosKotItemEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    kotBatchId = batchId,
                    tableNo = tableNo,
                    productId = cart.productId,
                    name = cart.name,
                    categoryId = cart.categoryId,
                    categoryName = cart.categoryName,
                    parentId = cart.parentId,
                    isVariant = cart.isVariant,
                    basePrice = cart.basePrice,
                    finalPrice = finalPrice,
                    modifierTotal = modifierTotal,
                    quantity = cart.quantity,
                    taxRate = cart.taxRate,
                    taxType = cart.taxType,
                    note = cart.note,
                    modifiersJson = cart.modifiersJson,
                    kitchenPrintReq = cart.kitchenPrintReq,
                    kitchenPrinted = false,
                    status = "DONE",
                    createdAt = now
                )
            }

          //  Log.d("KOT_DEBUG", "---- MainKitchenViewmodel----source:${source}")
            kotRepository.insertItemsInBill(tableNo, items, role)
            kotRepository.syncBillCount(tableId)



            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val printItems = lockAndFetchBatch(batchId)

                    if (printItems.isNotEmpty()) {
                        printerManager.enqueueKitchen(
                            sessionKey = tableNo,
                            orderType = orderType,
                            items = printItems
                        )
                    }

                    tableKotSyncService.syncTableSnapshot(
                        tableId = tableNo,
                        source = source
                    )

                } catch (e: Exception) {
                    Log.e("ASYNC_TASK", "❌ Background failed", e)
                }
            }

//            withContext(Dispatchers.IO) {
//                try {
//                    val items = lockAndFetchBatch(batchId)
//                    if (items.isNotEmpty()) {
//                    //    Log.e("PRINT_FLOW", "🔥 Printing batch=$batchId items=${items.size}")
//                        printerManager.enqueueKitchen(
//                            sessionKey = tableNo,
//                            orderType = orderType,
//                            items = items
//                        )
//                    }
//
//                    tableKotSyncService.syncTableSnapshot(
//                        tableId = tableNo,
//                        source = source
//                    )
//
//                } catch (e: Exception) {
//                    Log.e("ASYNC_TASK", "❌ Background failed", e)
//                }
//            }


            true
        } catch (e: Exception) {
            Log.e("KOT", "❌ Failed to save KOT", e)
            false
        }
    }

//    @Transaction
//    suspend fun lockAndFetchBatch(batchId: String): List<PosKotItemEntity> {
//
//        val updated = kotItemDao.markBatchKitchenPrintedBatch(batchId)
//
//        if (BuildConfig.DEBUG) {
//            Log.e("LOCK_BATCH", "batch=$batchId updatedRows=$updated")
//        }
//        if (updated == 0) return emptyList()
//
//        return kotItemDao.getAllItemsByBatchId(batchId)
//    }


    @Transaction
    suspend fun lockAndFetchBatch(batchId: String): List<PosKotItemEntity> {

        // 1. fetch FIRST
        val items = kotItemDao.getItemsByBatchId(batchId)

        if (items.isEmpty()) return emptyList()

        // 2. then mark as printed
        val updated = kotItemDao.markBatchKitchenPrintedBatch(batchId)

        if (BuildConfig.DEBUG) {
            Log.e("LOCK_BATCH", "batch=$batchId updatedRows=$updated")
        }

        return items
    }

    fun replaceKotFromFirestoreWaiterListener(
        tableId: String,
        sessionId: String,
        items: List<Map<String, Any>>,
        source: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                // 🚫 SAFETY: Only process Firestore data here
                if (source != "FIRESTORE") {
                    Log.d("SYNC_VM", "⛔ Ignored non-firestore source")
                    return@launch
                }

                // 🔥 STEP 1: DELETE OLD ITEMS
                kotRepository.deleteKotByTable(tableId)

                if (items.isEmpty()) {
                    Log.d("SYNC_VM", "🪹 Table empty after delete: $tableId")
                    return@launch
                }

                // 🔥 STEP 2: MAP ITEMS
                val cartList = items.map { item ->
                    PosCartEntity(
                        sessionId = sessionId,
                        tableId = tableId,
                        productId = item["productId"]?.toString() ?: "",
                        name = item["name"]?.toString() ?: "",
                        categoryId = "",
                        categoryName = item["category"]?.toString() ?: "",
                        parentId = null,
                        isVariant = false,
                        basePrice = (item["price"] as? Number)?.toDouble() ?: 0.0,
                        finalPrice = 0.0,
                        modifierTotal = 0.0,
                        quantity = (item["quantity"] as? Number)?.toInt() ?: 1,
                        taxRate = 0.0,
                        taxType = "exclusive",
                        note = item["note"]?.toString() ?: "",
                        modifiersJson = "",
                        kitchenPrintReq = false, // 🚫 IMPORTANT (avoid reprint loop)
                        createdAt = System.currentTimeMillis()
                    )
                }


                // 🔥 STEP 3: SAVE LOCALLY (NO FIRESTORE SYNC)
                saveCartItemToBillView(
                    orderType = "DINE_IN",
                    sessionId = sessionId,
                    tableNo = tableId,
                    cartItems = cartList,
                    deviceId = "FIRESTORE_SYNC",
                    deviceName = "FIRESTORE_SYNC",
                    appVersion = "FIRESTORE_SYNC",
                    role = "FIRESTORE_TABLE"
                )

            } catch (e: Exception) {
                Log.e("SYNC_VM", "❌ replaceKotFromFirestore failed", e)
            }
        }
    }

    private suspend fun saveCartItemToBillView(
        orderType: String,
        sessionId: String,
        tableNo: String?,
        cartItems: List<PosCartEntity>,
        deviceId: String,
        deviceName: String?,
        appVersion: String?,
        role: String,
    ): Boolean = withContext(Dispatchers.IO) {

        val tableNo = tableNo?: "";
        try {
            val db = AppDatabaseProvider.get(printerManager.appContext())
            val kotBatchDao = db.kotBatchDao()
            val kotItemDao = db.kotItemDao()

            val batchId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            repository.markAllSent(tableNo)

            val batch = PosKotBatchEntity(
                id = batchId,
                sessionId = sessionId,
                tableNo = tableNo,
                orderType = orderType,
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                createdAt = now,
                sentBy = "WAITRER",
                syncStatus = "DONE",
                lastSyncedAt = null
            )

            kotBatchDao.insert(batch)

            Log.d("KOT_DEBUG", "--- WaiterKitchenViewmodel----")

//            cartItems.forEach {
//                Log.d(
//                    "TABLE_SNAPSHOOT_DEBUG",
//                    "Item: ${it.name} qty=${it.quantity} note=${it.note}"
//                )
//            }

            val items = cartItems.map { cart ->
                //    Log.d("KOT_DEBUG", "Saving item: ${cart.name} qty=${cart.quantity}")
                PosKotItemEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    kotBatchId = batchId,
                    tableNo = tableNo,
                    productId = cart.productId,
                    name = cart.name,
                    categoryId = cart.categoryId,
                    categoryName = cart.categoryName,
                    parentId = cart.parentId,
                    isVariant = cart.isVariant,
                    basePrice = cart.basePrice,
                    finalPrice = 0.0,
                    modifierTotal = 0.0,
                    quantity = cart.quantity,
                    taxRate = cart.taxRate,
                    taxType = cart.taxType,
                    note = cart.note,
                    modifiersJson = cart.modifiersJson,
                    kitchenPrinted = true,
                    status = "DONE",
                    createdAt = now
                )
            }

            kotRepository.insertItemsInBill(tableNo, items,role)
            kotRepository.markDoneAll(tableNo)
            kotRepository.syncKinchenCount(tableNo)
            kotRepository.syncBillCount(tableNo)

            true

        } catch (e: Exception) {
            Log.e("KOT", "❌ Failed to save KOT", e)
            false
        }
    }





}


