package com.it10x.foodappgstav7_15.ui.bill

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.it10x.foodappgstav7_15.data.PrinterRole
import com.it10x.foodappgstav7_15.data.online.repository.CashierOrderSyncRepository
import com.it10x.foodappgstav7_15.data.online.sync.SyncManagerProvider
import com.it10x.foodappgstav7_15.data.online.sync.TableKotSyncService
import com.it10x.foodappgstav7_15.data.pos.dao.KotItemDao
import com.it10x.foodappgstav7_15.data.pos.dao.OrderMasterDao
import com.it10x.foodappgstav7_15.data.pos.dao.OrderProductDao
import com.it10x.foodappgstav7_15.data.pos.dao.OutletDao
import com.it10x.foodappgstav7_15.data.pos.entities.PosKotItemEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosOrderItemEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosOrderMasterEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosOrderPaymentEntity
import com.it10x.foodappgstav7_15.data.pos.repository.OrderSequenceRepository
import com.it10x.foodappgstav7_15.data.pos.repository.OutletRepository
import com.it10x.foodappgstav7_15.data.pos.repository.POSOrdersRepository
import com.it10x.foodappgstav7_15.data.pos.repository.POSPaymentRepository
import com.it10x.foodappgstav7_15.printer.PrintOrderBuilder
import com.it10x.foodappgstav7_15.printer.PrinterManager
import com.it10x.foodappgstav7_15.printer.ReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.it10x.foodappgstav7_15.data.print.OutletInfo
import com.it10x.foodappgstav7_15.data.print.OutletMapper
import com.it10x.foodappgstav7_15.ui.payment.PaymentInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.it10x.foodappgstav7_15.data.pos.dao.PosCustomerDao
import com.it10x.foodappgstav7_15.data.pos.dao.PosCustomerLedgerDao
import com.it10x.foodappgstav7_15.data.pos.entities.PosCustomerEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosCustomerLedgerEntity
import com.it10x.foodappgstav7_15.data.pos.repository.KotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import com.it10x.foodappgstav7_15.data.pos.manager.TableSyncManager
import com.it10x.foodappgstav7_15.fiscal.FiscalContext
import com.it10x.foodappgstav7_15.fiscal.FiscalService
import com.it10x.foodappgstav7_15.fiscal.getFiscalService
import com.it10x.foodappgstav7_15.network.fiskaly.FiskalyClient
import com.it10x.foodappgstav7_15.fiskaly.FiskalyRepository
import com.it10x.foodappgstav7_15.network.model.ClientRequest
import com.it10x.foodappgstav7_15.network.model.PaymentAmount
import com.it10x.foodappgstav7_15.network.model.StartTransactionRequest
import com.it10x.foodappgstav7_15.network.model.VatAmount
import com.it10x.foodappgstav7_15.storage.TssStorage
import com.it10x.foodappgstav7_15.utils.MoneyUtils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow


class BillViewModel(
    private val kotItemDao: KotItemDao,
    private val orderMasterDao: OrderMasterDao,
    private val orderProductDao: OrderProductDao,
    private val orderSequenceRepository: OrderSequenceRepository,
    private val outletDao: OutletDao,
    private val tableId: String,
    private val tableName: String,
    private val orderType: String,
    private val repository: POSOrdersRepository,
    private val printerManager: PrinterManager,
    private val outletRepository: OutletRepository,
    private val paymentRepository: POSPaymentRepository,
    private val customerDao: PosCustomerDao,
    private val ledgerDao: PosCustomerLedgerDao,
    private val kotRepository: KotRepository,
    private val cashierOrderSyncRepository: CashierOrderSyncRepository,
    private val tableSyncManager: TableSyncManager,
    private val fiskalyRepository: FiskalyRepository
) : ViewModel() {

    // --------------------------------------------------------
    // UI State + Delivery Address
    // --------------------------------------------------------
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    private val _deliveryAddress = MutableStateFlow<DeliveryAddressUiState?>(null)

    private val _loading = MutableStateFlow(false)
    val deliveryAddress: DeliveryAddressUiState? get() = _deliveryAddress.value

    private val _uiState = MutableStateFlow(BillUiState(loading = true))
    val uiState: StateFlow<BillUiState> = _uiState

    private val _currencySymbol = MutableStateFlow("₹") // fallback
    val currencySymbol: StateFlow<String> = _currencySymbol

    private val _discountFlat = MutableStateFlow(0.0)
    private val _deliveryFee = MutableStateFlow(0.0)
    private val _deliveryTaxPercent = MutableStateFlow(0.0)
    private val _discountPercent = MutableStateFlow(0.0)

    private val _customerSuggestions = MutableStateFlow<List<PosCustomerEntity>>(emptyList())
    val customerSuggestions: StateFlow<List<PosCustomerEntity>> = _customerSuggestions


    // ---------------- PAYMENT PROTECTION ----------------

//    private val _event = MutableStateFlow<String?>(null)
//    val event: StateFlow<String?> = _event

    private val _event = MutableSharedFlow<String>()
    val event = _event.asSharedFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val firestore = FirebaseFirestore.getInstance()

    val totalPaise: Long
        get() = MoneyUtils.toPaise(_uiState.value.total)
    val unpaidModes = setOf("CREDIT", "DELIVERY_PENDING", "WAITER_PENDING")

    private val _creditPaise = MutableStateFlow(0L)
    val creditPaise: StateFlow<Long> = _creditPaise


    fun setCreditAmountRaw(input: String) {
        val clean = input.trim()

        if (clean.isEmpty()) {
            _creditPaise.value = 0L
            return
        }

        val parts = clean.split(".")

        val rupees = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val paisePart = parts.getOrNull(1)?.padEnd(2, '0')?.take(2) ?: "00"
        val paise = paisePart.toLongOrNull() ?: 0L

        val finalPaise = rupees * 100 + paise

        Log.d("BILL_DEBUG", "RAW INPUT: $input → $finalPaise paise")

        _creditPaise.value = finalPaise
    }

    fun clearCredit() {
        Log.d("BILL_DEBUG", "CLEAR CREDIT")
        _creditPaise.value = 0L   // ✅ correct source of truth
    }


    fun setDeliveryFee(value: Double) {
        _deliveryFee.value = value.coerceAtLeast(0.0)
    }

    fun setDeliveryTaxPercent(value: Double) {
        _deliveryTaxPercent.value = value.coerceAtLeast(0.0)
    }

    // ✅ FINAL remaining flow (AUTO updates)
    val remainingPaise: StateFlow<Long> =
        combine(_uiState, _creditPaise) { ui, credit ->

            val totalPaise = MoneyUtils.toPaise(ui.total)

            val remaining = (totalPaise - credit).coerceAtLeast(0)

            Log.d("BILL_DEBUG", "Total: $totalPaise | Credit: $credit | Remaining: $remaining")

            remaining
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0L
        )





    private val tableKotSyncService = TableKotSyncService(
        firestore,
        kotItemDao
    )
//    private fun sendEvent(message: String) {
//        _event.value = message
//    }
    private fun sendEvent(message: String) {
        viewModelScope.launch {
            _event.emit(message)
        }
    }

//    fun clearEvent() {
//        _event.value = null
//    }

    fun setFlatDiscount(value: Double) {
        _discountFlat.value = value.coerceAtLeast(0.0)
        _discountPercent.value = 0.0 // reset percent
    }

    fun setPercentDiscount(value: Double) {
        _discountPercent.value = value.coerceAtLeast(0.0)
        _discountFlat.value = 0.0 // reset flat
    }

    fun setCustomerPhone(phone: String) {
        _uiState.update {
            it.copy(customerPhone = phone)
        }
    }



  //  val outletInfo: StateFlow<OutletInfo> = outletRepository.outletInfo
    // ✅ Expose orderType safely for Compose UI
    val orderTypePublic: String
        get() = orderType

    init {
        resetBillUi()
        observeBill()
        loadCurrency()
        setDeliveryTaxPercent(5.0)
    }


    // --------------------------------------------------------
    // Observe Bill (Live billing snapshot)
    // --------------------------------------------------------

    private fun observeBill() {
        viewModelScope.launch {
            combine(
                kotItemDao.getItemsForTable(tableId),
                _discountFlat,
                _discountPercent,
                _deliveryFee,
                _deliveryTaxPercent
            ) { kotItems, flat, percent, deliveryFeeFlow, deliveryTaxPercentFlow ->

                // return all values as one object
                BillCombine(
                    kotItems,
                    flat,
                    percent,
                    deliveryFeeFlow,
                    deliveryTaxPercentFlow
                )
            }.collectLatest { (kotItems, flat, percent, deliveryFeeFlow, deliveryTaxPercentFlow) ->

                val doneItems = kotItems.filter { it.status == "DONE" }

                val billingItems = doneItems
                    .groupBy {
                        listOf(
                            it.productId,
                            it.basePrice,
                            it.taxRate,
                            it.note,
                            it.modifiersJson
                        )
                    }
                    .map { (_, group) ->

                        val first = group.first()
                        val quantity = group.sumOf { it.quantity }
                        val modifierPricePerItem =
                            ModifierJsonHelper.fromJson(first.modifiersJson)
                                .flatMap { it.items }
                                .sumOf { it.price }

// ✅ base + modifier
                        val basePlusModifier = first.basePrice + modifierPricePerItem

// ✅ totals
                        val itemTotal = (basePlusModifier * quantity).round(2)

                        val taxPerItem =
                            if (first.taxType == "exclusive")
                                (basePlusModifier * (first.taxRate / 100)).round(2)
                            else 0.0

                        val taxTotal = (taxPerItem * quantity).round(2)



                        BillingItemUi(
                            id = first.id,
                            productId = first.productId,
                            name = first.name,
                            basePrice = first.basePrice,
                            taxRate = first.taxRate,
                            quantity = quantity,

                            // ✅ FIXED
                            finalTotal = itemTotal + taxTotal,
                            itemtotal = itemTotal,
                            taxTotal = taxTotal,

                            note = first.note ?: "",
                            modifiersJson = first.modifiersJson ?: ""
                        )

                    }





                val subtotal = billingItems.sumOf { it.itemtotal }
                val totalTax = billingItems.sumOf { it.taxTotal }

                val percentValue = subtotal * (percent / 100.0)
                val discount = if (flat > 0) flat else percentValue

                val safeDiscount = discount.coerceAtMost(subtotal)

                val taxAfterDiscount =
                    if (subtotal == 0.0) 0.0
                    else totalTax * (1 - safeDiscount / subtotal)
                val deliveryFee = deliveryFeeFlow
                val deliveryTax = deliveryFee * (deliveryTaxPercentFlow / 100.0)
                val finalTotal =
                    (subtotal - safeDiscount) +
                            taxAfterDiscount +
                            deliveryFee +
                            deliveryTax

                _uiState.update { old ->

                    old.copy(
                        loading = false,
                        items = billingItems,
                        subtotal = subtotal,
                        deliveryFee = deliveryFee,
                        deliveryTax = deliveryTax,
                        tax = taxAfterDiscount,
                        discountFlat = flat,
                        discountPercent = percent,
                        discountApplied = safeDiscount,
                        total = finalTotal
                    )
                }

            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        resetBillUi()
    }

    fun resetBillUi() {
        _discountFlat.value = 0.0
        _discountPercent.value = 0.0

        _deliveryFee.value = 0.0
        _deliveryTaxPercent.value = 5.0   // or 0.0 if you want

        _creditPaise.value = 0L

        _deliveryAddress.value = null

        _uiState.update {
            it.copy(
                customerPhone = ""
            )
        }
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            val outletInfo = outletRepository.getOutletInfo()
            _currencySymbol.value = outletInfo.currencyCode
        }
    }



    suspend fun hasPendingKitchenItems(): Boolean {
        return kotItemDao.countKitchenPending(tableId) > 0
    }
    // --------------------------------------------------------
    // Payment + Order Creation
    // --------------------------------------------------------

    private val paymentMutex = kotlinx.coroutines.sync.Mutex()
    fun payBill(
        payments: List<PaymentInput>,
        name: String,
        phone: String
    ) {
        Log.d("PAY_DEBUG", "creditPaise BEFORE payment = ${_creditPaise.value}")
        val creditPaiseInput = _creditPaise.value
        val paidModes = setOf("CASH", "CARD", "UPI", "WALLET")


        val hasPaid = payments.any { it.mode in paidModes }
        val hasUnpaid = payments.any { it.mode in unpaidModes }



        viewModelScope.launch {

            if (!paymentMutex.tryLock()) {
                sendEvent("Payment already in progress")
                return@launch
            }
            _isProcessing.value = true

            val outlet = outletDao.getOutlet()
            if (outlet == null) {

                _toastEvent.emit("Outlet not configured")
                _isProcessing.value = false
                return@launch
            }


            lateinit var fiscalService: FiscalService
            lateinit var fiscalContext: FiscalContext

            try {

            val inputPhone = phone.trim()
            val inputName = name.trim().ifBlank { "Customer" }

            val kotItems = kotItemDao
                .getItemsForTableSync(tableId)
                .filter { it.status == "DONE" }
                if (kotItems.isEmpty()) {
                    sendEvent("No items to bill")
                    return@launch
                }


                val itemSubtotalPaise = kotItems.sumOf {

                    val modifierPricePerItem =
                        ModifierJsonHelper.fromJson(it.modifiersJson)
                            .flatMap { group -> group.items }
                            .sumOf { item -> item.price }

                    val basePlusModifier = it.basePrice + modifierPricePerItem

                    MoneyUtils.toPaise(basePlusModifier) * it.quantity
                }




                val rawTaxPaise = kotItems
                    .filter { it.taxType == "exclusive" }
                    .sumOf {

                        val modifierPricePerItem =
                            ModifierJsonHelper.fromJson(it.modifiersJson)
                                .flatMap { group -> group.items }
                                .sumOf { item -> item.price }

                        val basePlusModifier = it.basePrice + modifierPricePerItem

                        val basePaise = MoneyUtils.toPaise(basePlusModifier)

                        val exactTaxPerItem = (basePaise * it.taxRate) / 100.0

                        exactTaxPerItem * it.quantity
                    }
                    .toLong()



                val flatPaise = MoneyUtils.toPaise(_discountFlat.value)
                val percentPaise =
                    ((itemSubtotalPaise * _discountPercent.value) / 100.0).roundToLong()

                val discountPaise =
                    if (flatPaise > 0) flatPaise else percentPaise

                val safeDiscountPaise =
                    discountPaise.coerceAtMost(itemSubtotalPaise)

                val discountRatio =
                    if (itemSubtotalPaise == 0L) 0.0
                    else safeDiscountPaise.toDouble() / itemSubtotalPaise.toDouble()

                val taxAfterDiscountPaise =
                    (rawTaxPaise * (1 - discountRatio)).roundToLong()

            val now = System.currentTimeMillis()
            val orderId = UUID.randomUUID().toString()

            val srno = orderSequenceRepository.nextOrderNo(
                outletId = outlet.outletId,
                businessDate = SimpleDateFormat(
                    "yyyyMMdd",
                    Locale.getDefault()
                ).format(Date())
            )


                val deliveryFeePaise = MoneyUtils.toPaise(_deliveryFee.value)
                val deliveryTaxPaise = (deliveryFeePaise * _deliveryTaxPercent.value / 100.0).roundToLong()
                val totalTaxPaise = taxAfterDiscountPaise + deliveryTaxPaise



                val grandTotalPaise =
                    itemSubtotalPaise -
                            safeDiscountPaise +
                            totalTaxPaise +
                            deliveryFeePaise
            // ===========================
            // PAYMENT CALCULATION
            // ===========================
                val totalPaidPaise = payments
                    .filter { it.mode in paidModes }
                    .sumOf { it.amount }

// 👇 FIRST define paymentStatus
                val hasCredit = creditPaiseInput > 0

                val paymentStatus = when {

                    payments.any { it.mode == "WAITER_PENDING" } -> "WAITER_PENDING"

                    payments.any { it.mode == "DELIVERY_PENDING" } -> "DELIVERY_PENDING"

                    hasPaid && hasCredit -> "PARTIAL"

                    hasCredit -> "CREDIT"

                    else -> "PAID"
                }


// 👇 THEN use it
                val duePaise = creditPaiseInput

                val dueAmount = BigDecimal.valueOf(creditPaiseInput, 2)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toDouble()

            // ===========================
            // PHONE VALIDATION
            // ===========================

                if (paymentStatus in listOf("CREDIT", "PARTIAL") && inputPhone.isBlank())
                 {
                    sendEvent("Phone required for credit sale")
                    return@launch
                }

// ===========================
// ENSURE CUSTOMER EXISTS (IF PHONE ENTERED)
// ===========================

            var resolvedCustomerId: String? = null

            if (inputPhone.isNotBlank()) {

                resolvedCustomerId = inputPhone

                val existingCustomer = customerDao.getCustomerByPhone(inputPhone)

                if (existingCustomer == null) {

                    val customer = PosCustomerEntity(
                        id = inputPhone,
                        ownerId = outlet.ownerId,
                        outletId = outlet.outletId,
                        name = inputName,
                        phone = inputPhone,
                        addressLine1 = null,
                        addressLine2 = null,
                        city = null,
                        state = null,
                        zipcode = null,
                        landmark = null,
                        creditLimit = 0.0,
                        currentDue = 0.0,   // 🔥 important
                        createdAt = now,
                        updatedAt = null
                    )

                    customerDao.insert(customer)
                }
            }
            // ===========================
            // CUSTOMER CREDIT HANDLING
            // ===========================

                if (paymentStatus == "CREDIT" || paymentStatus == "PARTIAL") {

                    val cleanPhone = inputPhone.trim()
                    resolvedCustomerId = cleanPhone

                    val creditToAdd = duePaise / 100.0

                    Log.d("LEDGER_DEBUG", "Running DB block")

                    val existingCustomer = customerDao.getCustomerByPhone(cleanPhone)

                    if (existingCustomer == null) {
                        Log.e("CREDIT", "❌ Customer NOT FOUND for phone = $cleanPhone")
                        return@launch
                    }


                    // ✅ FIX: use ID (NOT phone)
                    customerDao.increaseDue(existingCustomer.id, creditToAdd)

                    val updatedCustomer = customerDao.getCustomerById(existingCustomer.id)

                    val lastBalance = ledgerDao.getLastBalance(existingCustomer.id) ?: 0.0
                    val newBalance = lastBalance + creditToAdd

                    val ledgerEntry = PosCustomerLedgerEntity(
                        id = UUID.randomUUID().toString(),
                        ownerId = outlet.ownerId,
                        outletId = outlet.outletId,
                        customerId = existingCustomer.id,   // ✅ FIX
                        orderId = orderId,
                        paymentId = null,
                        type = "ORDER",
                        debitAmount = creditToAdd,
                        creditAmount = 0.0,
                        balanceAfter = newBalance,
                        note = "Credit sale Order #$srno",
                        createdAt = now,
                        deviceId = "POS"
                    )

                    ledgerDao.insert(ledgerEntry)

                }

            val paymentMode =
                if (payments.size > 1) "MIXED"
                else payments.firstOrNull()?.mode ?: "CREDIT"

            // ===========================
            // ORDER MASTER
            // ===========================

                val calculatedTotal = totalPaidPaise + creditPaiseInput
                val diff = kotlin.math.abs(calculatedTotal - grandTotalPaise)

//                if (diff > 13) { // 1 paise tolerance
//                    sendEvent("Payment mismatch")
//                    _isProcessing.value = false
//                    return@launch
//                }



            val orderMaster = PosOrderMasterEntity(
                id = orderId,
                srno = srno,
                orderType = orderType,
                tableNo = tableName,
                customerName = inputName,
                customerPhone = inputPhone,
                customerId = resolvedCustomerId,

                // keeping delivery address untouched
                dAddressLine1 = deliveryAddress?.line1,
                dAddressLine2 = deliveryAddress?.line2,
                dCity = deliveryAddress?.city,
                dState = deliveryAddress?.state,
                dZipcode = deliveryAddress?.zipcode,
                dLandmark = deliveryAddress?.landmark,

                itemTotal = MoneyUtils.fromPaise(itemSubtotalPaise),

               //taxTotal = MoneyUtils.fromPaise(taxTotalPaise),
                deliveryFee = _deliveryFee.value,
               // deliveryTax = ( _deliveryFee.value * _deliveryTaxPercent.value / 100.0 ),
                deliveryTax = MoneyUtils.fromPaise(deliveryTaxPaise),
                itemTax = MoneyUtils.fromPaise(taxAfterDiscountPaise),
                taxTotal = MoneyUtils.fromPaise(totalTaxPaise),
                discountTotal = MoneyUtils.fromPaise(safeDiscountPaise),
              //  grandTotal = grandTotal,
                grandTotal = MoneyUtils.fromPaise(grandTotalPaise),
                paymentMode = paymentMode,
                paymentStatus = paymentStatus,
               // paidAmount = paidAmount,
                paidAmount = MoneyUtils.fromPaise(totalPaidPaise),

                dueAmount =dueAmount,

                orderStatus = "COMPLETED",

                deviceId = "POS",
                deviceName = "POS",
                appVersion = "1.0",

                createdAt = now,
                updatedAt = now,

                syncStatus = if (paymentStatus == "WAITER_PENDING") "SYNCED" else "PENDING",
                lastSyncedAt = null,
                notes = null
            )

                val orderItems = kotItems
                    .groupBy {
                        listOf(
                            it.productId,
                            it.basePrice,
                            it.taxRate,
                            it.note,
                            it.modifiersJson
                        )
                    }
                    .map { (_, group) ->

                        val first = group.first()
                        val quantity = group.sumOf { it.quantity }

                        val modifierPricePerItem =
                            ModifierJsonHelper.fromJson(first.modifiersJson)
                                .flatMap { it.items }
                                .sumOf { it.price }
                     //   val itemGrossAmount =
                     //       ((first.basePrice + modifierPricePerItem) * quantity).round(2)

                   //     val basePlusModifier = first.basePrice + modifierPricePerItem

                        val basePlusModifier = first.basePrice + modifierPricePerItem

                        val itemGrossAmount = (basePlusModifier * quantity).round(2)


                        val taxPerItemPlusModifier =
                            if (first.taxType == "exclusive")
                                (basePlusModifier * (first.taxRate / 100))
                            else 0.0

                        val finalPricePerItemPlusModifier =
                            (basePlusModifier + taxPerItemPlusModifier).round(2)

                        val finalPriceTotalItemPlusModifier =
                            (finalPricePerItemPlusModifier * quantity).round(2)

                        val taxTotalItemPlusModifier =
                            (taxPerItemPlusModifier * quantity).round(2)

                        val modifierTotal =
                            (modifierPricePerItem * quantity).round(2)
                        Log.d("BASE_PRICE", "${first.basePrice}")

                         PosOrderItemEntity(
                            id = UUID.randomUUID().toString(),
                            // 🔹 SNAPSHOT CATEGORY NAME (enterprise safe)
                            categoryName = first.categoryName,
                            orderMasterId = orderId,
                            productId = first.productId,
                            name = first.name,
                            categoryId = first.categoryId,
                            parentId = first.parentId,
                            isVariant = first.isVariant,
                            basePrice = first.basePrice,
                            modifierPrice = modifierTotal,
                            quantity = quantity,
                            itemSubtotal = itemGrossAmount ,
                            // 🔹 Currency snapshot (important for audit)
                            currency = _currencySymbol.value,
                            // 🔹 Payment snapshot (do NOT rely on join later)
                            paymentStatus = paymentStatus,
                            taxRate = first.taxRate,
                            taxType = first.taxType,
                            taxAmountPerItem = taxPerItemPlusModifier,
                            taxTotal = taxTotalItemPlusModifier,
                            note = first.note,
                            modifiersJson = first.modifiersJson,
                            finalPricePerItem = finalPricePerItemPlusModifier,
                            finalTotal = finalPriceTotalItemPlusModifier,
                            createdAt = now
                        )
                    }

               // val (txId, clientId) = fiskalyRepository.startTransaction()
                //fiscalService = getFiscalService(outlet.countryName!!, fiskalyRepository)
                fiscalService = getFiscalService("IN", fiskalyRepository)
                fiscalContext = withContext(Dispatchers.IO) {
                    fiscalService.start()
                }
                withContext(Dispatchers.IO) {
                orderMasterDao.insert(orderMaster)
                orderProductDao.insertAll(orderItems)

                    if (payments.isNotEmpty() && totalPaidPaise > 0){
                    val paymentEntities = payments.map {
                       PosOrderPaymentEntity(
                            id = UUID.randomUUID().toString(),
                            orderId = orderId,
                            ownerId = outlet.ownerId,
                            outletId = outlet.outletId,
                            amount = MoneyUtils.fromPaise(it.amount),
                            mode = it.mode,
                            provider = null,
                            method = null,
                            status = "SUCCESS",
                            deviceId = "POS",
                            createdAt = now,
                            syncStatus = "PENDING"
                        )
                    }

                    paymentRepository.insertPayments(paymentEntities)
                }

//MANAGE TABLE AFTER ORDER COMPLITE
                    repository.finalizeTableAfterPayment(
                        tableNo = tableId,
                        orderType = orderType
                    )

                    // ✅ 2. PRINT IMMEDIATELY
                    printOrder(orderMaster, orderItems)

                    // 🔥 FIRESTORE CLEAR
                    try {
                      //  tableKotSyncService.clearTableSnapshot(tableId)
                        SyncManagerProvider.get().addClearTable(tableId)
                        kotRepository.deleteKotByTable(tableId)
                        Log.d("TABLE_SYNC", "✅ Table cleared after payment")
                    } catch (e: Exception) {
                        Log.e("TABLE_SYNC", "❌ Failed to clear table", e)
                    }
            }
                //FISKLAY CODE
                withContext(Dispatchers.IO) {
                    fiscalService.finish(
                        fiscalContext,
                        payments,
                        kotItems
                    )
                }

                sendEvent("Payment successful")

                resetBillUi()
            } catch (e: Exception) {

//                if (::fiscalService.isInitialized && ::fiscalContext.isInitialized) {
//                    withContext(Dispatchers.IO) {
//                        fiscalService.cancel(fiscalContext)
//                    }
//                }

               // if (!isFinished) {
                    fiscalService.cancel(fiscalContext)
               // }

                Log.e("PAY_ERROR", "Payment failed", e)
                sendEvent("Payment failed")
            }finally {
                _isProcessing.value = false
                if (paymentMutex.isLocked) {
                    paymentMutex.unlock()
                }
            }

        }
    }

    fun Double.round(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return kotlin.math.round(this * factor) / factor
    }
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {

                // 1️⃣ Delete from KOT
                kotItemDao.deleteItemById(itemId)

                // 2️⃣ Update real table bill counters
                kotRepository.syncBillCount(tableId)

                // 3️⃣ Sync to correct table (DINE_IN / TAKEAWAY / DELIVERY)
                tableSyncManager.syncCart(tableId, orderType)
                tableSyncManager.syncBill(tableId, orderType)

                //  NEW: FIRESTORE TABLE SNAPSHOT SYNC (IMPORTANT)
                try {
//                        tableKotSyncService.syncTableSnapshot(
//                            tableId = tableId,
//                            source = "POS"
//                        )
                    SyncManagerProvider.get().addTableUpdate(tableId)

                } catch (e: Exception) {
                    Log.e("TABLE_SYNC", "Failed to trigger snapshot sync", e)
                }

            } catch (e: Exception) {
                Log.e("DELETE", "Failed to delete item", e)
            }
        }
    }



    // --------------------------------------------------------
    // Set Delivery Address
    // --------------------------------------------------------
    fun setDeliveryAddress(address: DeliveryAddressUiState) {
        _deliveryAddress.value = address
    }

    // --------------------------------------------------------
    // Printing (Unified print pipeline)
    // --------------------------------------------------------
    private suspend fun printOrder(
        order: PosOrderMasterEntity,
        items: List<PosOrderItemEntity>,

    ) = withContext(Dispatchers.IO) {
        val printOrder = PrintOrderBuilder.build(order, items)

        val outlet = outletDao.getOutlet()
        if (outlet == null) {
            withContext(Dispatchers.Main) {
                sendEvent("Please configure outlet first")
            }
            return@withContext
        }
        val outletInfo = OutletMapper.fromEntity(outlet)

    //    printerManager.printTextNewSuspend(PrinterRole.BILLING, printOrder, outletInfo)
        printerManager.enqueueBill(printOrder,order.paymentMode, outletInfo)
          }

    fun getDoneItems(orderRef: String, orderType: String): Flow<List<PosKotItemEntity>> {
        return kotItemDao.getDoneItemsForTable(orderRef)
    }


    // file: BillViewModel.kt (inside the class)
    fun updateItemQuantity(itemId: String, newQty: Int) {

        viewModelScope.launch {

            val qty = newQty.coerceAtLeast(0)

            val targetUi = _uiState.value.items
                .find { it.id == itemId }

            if (targetUi == null) {
                Log.d("EDIT_DEBUG", "❌ targetUi NOT FOUND")
                return@launch
            }

            val allItems = kotItemDao.getItemsForTableSync(tableId)




            val groupedItems = allItems.filter {
                it.productId == targetUi.productId &&
                        it.basePrice == targetUi.basePrice &&
                        it.taxRate == targetUi.taxRate &&
                        (it.note ?: "") == targetUi.note &&
                        (it.modifiersJson ?: "") == targetUi.modifiersJson &&
                        it.status == "DONE"
            }


            // Delete
            groupedItems.forEach {
                kotItemDao.deleteItemById(it.id)
            }



            if (qty > 0 && groupedItems.isNotEmpty()) {

                val template = groupedItems.first()

                kotItemDao.insert(
                    template.copy(
                        id = UUID.randomUUID().toString(),
                        quantity = qty
                    )
                )

                Log.d("EDIT_DEBUG", "Inserted new row qty=$qty")
            }

            val after = kotItemDao.getItemsForTableSync(tableId)

            //  NEW: FIRESTORE TABLE SNAPSHOT SYNC (IMPORTANT)
            try {
                tableKotSyncService.syncTableSnapshot(
                    tableId = tableId,
                    source = "POS"
                )

            } catch (e: Exception) {
                Log.e("TABLE_SYNC", "Failed to trigger snapshot sync", e)
            }

        }
    }


    private var searchJob: Job? = null

    fun observeCustomerSuggestions(phone: String) {

        if (phone.length < 3) {
            _customerSuggestions.value = emptyList()
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            customerDao.searchCustomersByPhone(phone)
                .collectLatest { result ->
                    _customerSuggestions.value = result
                   // Log.d("SUGGEST", "Found: ${result.size}")
                }
        }
    }



    fun clearCustomerSuggestions() {
        _customerSuggestions.value = emptyList()
    }

}
data class BillCombine(
    val items: List<PosKotItemEntity>,
    val flat: Double,
    val percent: Double,
    val deliveryFee: Double,
    val deliveryTaxPercent: Double
)