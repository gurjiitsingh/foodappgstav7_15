package com.it10x.foodappgstav7_15.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.it10x.foodappgstav7_15.data.PrinterConfig
import com.it10x.foodappgstav7_15.data.PrinterPreferences
import com.it10x.foodappgstav7_15.data.PrinterRole
import com.it10x.foodappgstav7_15.data.PrinterType
import com.it10x.foodappgstav7_15.data.print.OutletInfo
import com.it10x.foodappgstav7_15.printer.bluetooth.BluetoothPrinter
import com.it10x.foodappgstav7_15.printer.lan.LanPrinter
import com.it10x.foodappgstav7_15.printer.usb.USBPrinter
import com.it10x.foodappgstav7_15.data.print.OutletMapper
import com.it10x.foodappgstav7_15.data.pos.AppDatabaseProvider
import com.it10x.foodappgstav7_15.data.pos.entities.PosKotItemEntity
import com.it10x.foodappgstav7_15.ui.sales.SalesUiState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.it10x.foodappgstav7_15.printer.PrintJob
import kotlinx.coroutines.runBlocking
import com.it10x.foodappgstav7_15.data.printqueue.PrintQueueDao
import com.it10x.foodappgstav7_15.printer.queue.PrintQueueManager
import com.it10x.foodappgstav7_15.printer.utils.QrUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrinterManager private constructor(
    private val context: Context
) {

    companion object {
        @Volatile
        private var INSTANCE: PrinterManager? = null

        fun getInstance(context: Context): PrinterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrinterManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }




    private val scope = CoroutineScope(Dispatchers.IO)

    private val queueManager: PrintQueueManager by lazy {
        val db = AppDatabaseProvider.get(context)
        PrintQueueManager(
            dao = db.printQueueDao(),
            printerManager = this
        )
    }

    private val prefs by lazy { PrinterPreferences(context) }
    fun appContext(): Context = context.applicationContext


    fun enqueuePrint(
        role: PrinterRole,
        text: String,
        paymentMode: String? = null,
        grandTotal: Double? = null
    ) {
       // Log.e("PRINT_DEBUG", "🔥 enqueuePrint CALLED role=$role")
        scope.launch {
            queueManager.enqueue(role, text, paymentMode, grandTotal)
        }
    }

    fun enqueueBill(
        order: PrintOrder,
        paymentMode: String,
        outletInfo: OutletInfo
    ) {
        val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"

        val receiptText = when (size) {
            "80mm" -> ReceiptFormatter.billing48(order, outletInfo)
            else -> ReceiptFormatter.billing(order, outletInfo)
        }

       // enqueuePrint(PrinterRole.BILLING, receiptText)

        enqueuePrint(
            role = PrinterRole.BILLING,
            text = receiptText,
            paymentMode = paymentMode,
            grandTotal = order.grandTotal
        )
    }



    fun enqueueKitchen(
        sessionKey: String,
        orderType: String,
        items: List<PosKotItemEntity>
    ) {

        val text = ReceiptFormatter.posKitchen(
            sessionKey = sessionKey,
            orderType = orderType,
            items = items,

            )

        enqueuePrint(PrinterRole.KITCHEN, text)
    }

    // --------------------------------
    // NEW PRINT JOB STRATAGY
    // --------------------------------


    fun print(job: PrintJob, onResult: (Boolean) -> Unit = {}) {
        when (job) {



            is PrintJob.SalesReport -> {

                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32


                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.salesFullReport(
                    state = job.state,
                    info = info,
                    width = width,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }


            is PrintJob.CategoryWiseSalesReport -> {

                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.categoryWiseSalesReport(
                    categorySales = job.categorySales,
                    info = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }

            is PrintJob.SingleCategoryDetail -> {
                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.salesBySingleCategory(
                    category = job.category,
                    items = job.items,
                    outletInfo = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }

            // ✅ NEW: Category Summary


            is PrintJob.TotalSalesReport -> {

                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.totalSalesReport(
                    beforeDiscount = job.beforeDiscount,
                    discount = job.discount,
                    afterDiscount = job.afterDiscount,
                    tax = job.tax,
                    info = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }


            is PrintJob.CategorySummary -> {

                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.salesCategorySummary(
                    category = job.category,
                    totalQty = job.qty,
                    totalAmount = job.amount,
                    info = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }

            is PrintJob.ProductSummary -> {
                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.salesProductSummary(
                    product = job.product,
                    qty = job.qty,
                    amount = job.amount,
                    info = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }

            // ✅ ADD THIS BLOCK
            is PrintJob.CategoryProductReport -> {

                val size = prefs.getPrinterSize(PrinterRole.BILLING) ?: "80mm"
                val width = if (size == "80mm") 48 else 32

                val info = getOutletInfoOrNull()
                if (info == null) {
                    onResult(false)
                    return
                }

                val text = ReceiptFormatter.salesCategoryProductList(
                    category = job.category,
                    items = job.items,
                    outletInfo = info,
                    width = width,
                    fromMillis = job.fromMillis,
                    toMillis = job.toMillis,
                    printMillis = job.printMillis
                )

                printText(PrinterRole.BILLING, text)
            }
        }
    }





    // --------------------------------
    // TEST PRINT (already OK)
    // --------------------------------
    fun printTest(
        config: PrinterConfig,
        onResult: (Boolean) -> Unit
    ) {
        val roleLabel = config.role.name

        when (config.type) {

            PrinterType.BLUETOOTH -> {
                //    Log.d("PRINT_BT", "Test BT address='${config.bluetoothAddress}'")
                if (config.bluetoothAddress.isBlank()) {
                    onResult(false)
                    return
                }
                BluetoothPrinter.printTest(
                    config.bluetoothAddress,
                    roleLabel,
                    onResult
                )
            }

            PrinterType.LAN -> {
                if (config.ip.isBlank()) {
                    onResult(false)
                    return
                }
                LanPrinter.printTest(
                    config.ip,
                    config.port,
                    roleLabel,
                    onResult
                )
            }



            PrinterType.USB -> {
                val device = config.usbDevice ?: run {
                    onResult(false)
                    return
                }

                USBPrinter.printTest(
                    context = context,
                    device = device,
                    roleLabel = roleLabel,
                    onResult = onResult
                )
            }






            PrinterType.WIFI -> onResult(false)
        }
    }

    // --------------------------------
    // REAL PRINT (USED BY BUTTON + AUTO)
    // --------------------------------
    fun printText(
        role: PrinterRole,
        text: String,
        paymentMode: String? = null,
        grandTotal: Double? = null,
        onResult: (Boolean) -> Unit = {}
    ) {

        Log.e(
            "PRINTTEST",
            "\n================= printText =================\n$text\n=================================================="
        )
        val config = prefs.getPrinterConfig(role)
        if (config == null) {
            Log.e("PRINTTEST", "No printer configured for role=$role")
            onResult(false)
            return
        }


        val outletInfo = getOutletInfoOrNull()

        val qrBitmap =
            if (role != PrinterRole.KITCHEN && outletInfo?.qrEnabled == true) {

                try {

                    if (
                        paymentMode == "UPI" &&
                        !outletInfo.upiId.isNullOrBlank()
                    ) {
                        // 🔥 UPI QR

                        val upiId = outletInfo.upiId!!
                        val name = outletInfo.upiName ?: "Shop"
                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")

                        val amount = String.format("%.2f", grandTotal)

                        val upiLink =
                            "upi://pay?pa=$upiId&pn=$encodedName&am=$amount&cu=INR"

                        QrUtils.generateQr(upiLink)

                    } else {
                        // 🔥 fallback QR (or null if you want)
                        QrUtils.loadSavedQr(context)
                    }

                } catch (e: Exception) {
                    Log.e("QR", "QR generation failed", e)
                    null
                }
            } else null






        when (config.type) {

//PRINT QR CODE FORM TEXT

            PrinterType.BLUETOOTH -> {
                if (config.bluetoothAddress.isBlank()) {
                    onResult(false)
                    return
                }

                try {
                    val isKitchen = role == PrinterRole.KITCHEN
                  //  val size = prefs.getPrinterSize(role) ?: "80mm"

                    // =============================
                    // LOAD LOGO
                    // =============================
         //           val logoFile = java.io.File(context.filesDir, "logo.png")

//                    val logoBitmap = if (logoFile.exists()) {
//                        android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
//                    } else null

//                    val targetWidth = if (size == "80mm") 384 else 384
//
//                    val resizedLogo = logoBitmap?.let {
//                        BluetoothPrinter.resizeBitmap(it, targetWidth)
//                    }

                    val logoBitmap = loadSavedLogo()

                    // =============================
                    // 🔥 GENERATE QR (NEW)
                    // =============================


                    // =============================
                    // 🔥 MAIN LOGIC
                    // =============================
                    if (!isKitchen && (logoBitmap != null || qrBitmap != null)) {
                        BluetoothPrinter.printLogoTextQr(
                            mac = config.bluetoothAddress,
                            logoBitmap = logoBitmap,
                            qrBitmap = qrBitmap,
                            text = text,
                            onResult = onResult
                        )
                    } else {
                        BluetoothPrinter.printText(
                            config.bluetoothAddress,
                            text,
                            onResult
                        )
                    }

                } catch (e: Exception) {
                    BluetoothPrinter.printText(
                        config.bluetoothAddress,
                        text,
                        onResult
                    )
                }
            }

            PrinterType.LAN -> {
                if (config.ip.isBlank()) {
                    onResult(false)
                    return
                }

                try {
                    val isKitchen = role == PrinterRole.KITCHEN

                   // val size = prefs.getPrinterSize(role) ?: "80mm"

                    val logoFile = java.io.File(context.filesDir, "logo.png")

//                    val bitmap = if (logoFile.exists()) {
//                        android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
//                    } else null

//                    val targetWidth = if (size == "80mm") 384 else 384
//
//                    val resizedLogo = bitmap?.let {
//                        BluetoothPrinter.resizeBitmap(it, targetWidth)
//                    }

                    val logoBitmap = loadSavedLogo()


                    if (!isKitchen && (logoBitmap != null || qrBitmap != null)) {



                        LanPrinter.printLogoTextQr(
                            ip = config.ip,
                            port = config.port,
                            logoBitmap = logoBitmap,
                            qrBitmap = qrBitmap,
                            text = text,
                            onResult = onResult
                        )

                    } else {

                        LanPrinter.printText(
                            config.ip,
                            config.port,
                            text,
                            onResult
                        )
                    }

                } catch (e: Exception) {
                    LanPrinter.printText(
                        config.ip,
                        config.port,
                        text,
                        onResult
                    )
                }
            }

            PrinterType.USB -> {

                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager

                val saved = prefs.getUSBPrinter(role)

                if (saved == null) {
                    Log.e("USB", "No saved USB printer")
                    onResult(false)
                    return
                }

                val (vendorId, productId) = saved

                val device = usbManager.deviceList.values.find {
                    it.vendorId == vendorId && it.productId == productId
                }

                if (device == null) {
                    Log.e("USB", "Device not found")
                    onResult(false)
                    return
                }

                if (!usbManager.hasPermission(device)) {
                    Log.e("USB", "No permission")
                    onResult(false)
                    return
                }

                try {
                    val isKitchen = role == PrinterRole.KITCHEN

                    val logoFile = java.io.File(context.filesDir, "logo.png")

//                    val bitmap = if (logoFile.exists()) {
//                        android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
//                    } else null

//                    val size = prefs.getPrinterSize(role) ?: "80mm"
//                    val targetWidth = if (size == "80mm") 384 else 384
//
//                    val resizedLogo = bitmap?.let {
//                        BluetoothPrinter.resizeBitmap(it, targetWidth)
//                    }

                    val logoBitmap = loadSavedLogo()


                  if (!isKitchen && (logoBitmap != null || qrBitmap != null)) {


                        USBPrinter.printLogoTextQrUSB(
                            context,
                            device,
                            logoBitmap,   // can be null
                            qrBitmap,      // 🔥 NEW
                            text,
                            onResult
                        )
                    } else {
                        USBPrinter.printText(
                            context,
                            device,
                            text,
                            onResult
                        )
                    }

                } catch (e: Exception) {
                    Log.e("USB", "Print failed", e)
                    onResult(false)
                }
            }

            PrinterType.WIFI -> onResult(false)
        }
    }



    fun printTextNew(
        role: PrinterRole,
        order: PrintOrder,
        onResult: (Boolean) -> Unit = {}
    ) {
        //  Log.e("PRINT_NEW", "Printing for role=$role")

        // Get printer configuration and preferences
        val config = prefs.getPrinterConfig(role)


        if (config == null) {
            Log.e("PPRINTTEST", "No printer configured for role=$role")
            onResult(false)
            return
        }

        // ✅ Select format based on page size
        val size = prefs.getPrinterSize(role) ?: "80mm"

        // ✅ Auto-load outlet info if not provided

        val info = getOutletInfoOrNull()
        if (info == null) {
            onResult(false)
            return
        }




        // ✅ Select format based on printer page size
        val receiptText = when (size) {
            "80mm" -> ReceiptFormatter.billing48(order, info)
            else -> ReceiptFormatter.billing(order, info)
        }


        Log.e(
            "PRINTTEST",
            "\n================= BILL NEWTEXT =================\n$receiptText\n=================================================="
        )

        // ✅ Printing logic (kept same as before)
        when (config.type) {
            PrinterType.BLUETOOTH -> {
                if (config.bluetoothAddress.isBlank()) {
                    Log.e("PRINT_NEW", "Bluetooth address missing")
                    onResult(false)
                    return
                }
                BluetoothPrinter.printText(
                    config.bluetoothAddress,
                    receiptText,
                    onResult
                )
            }

            PrinterType.LAN -> {
                if (config.ip.isBlank()) {
                    Log.e("PRINT_NEW", "LAN IP missing")
                    onResult(false)
                    return
                }
                LanPrinter.printText(
                    config.ip,
                    config.port,
                    receiptText,
                    onResult
                )
            }

            PrinterType.USB -> {

                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager

                val saved = prefs.getUSBPrinter(role)

                if (saved == null) {
                    Log.e("PRINT_NEW", "No saved USB printer")
                    onResult(false)
                    return
                }

                val (vendorId, productId) = saved

                val device = usbManager.deviceList.values.find {
                    it.vendorId == vendorId && it.productId == productId
                }

                if (device == null) {
                    Log.e("PRINT_NEW", "USB device not found")
                    onResult(false)
                    return
                }

                if (!usbManager.hasPermission(device)) {
                    Log.e("PRINT_NEW", "USB permission denied")
                    onResult(false)
                    return
                }

                // ✅ CORRECT CALL (with device)
                USBPrinter.printText(
                    context,
                    device,

                    receiptText,
                    onResult
                )
            }

            PrinterType.WIFI -> {
                Log.e("PRINT_NEW", "WiFi printing not supported yet")
                onResult(false)
            }
        }
    }





    fun printTextKitchen(
        role: PrinterRole,
        sessionKey: String,
        orderType: String,
        items: List<PosKotItemEntity>,
        onResult: (Boolean) -> Unit = {}
    ) {

        val config = prefs.getPrinterConfig(role)
        if (config == null) {
            Log.e("PRINTTEST", "No printer configured for role=$role")
            onResult(false)
            return
        }

        val text = ReceiptFormatter.posKitchen(
            sessionKey ,
            orderType,
            items
        )

        Log.e(
            "PRINTTEST",
            "\n================= KITCHEN RECEIPT =================\n$text\n=================================================="
        )

        //Log.d("PRINT", "Printing role=$role type=${config.type}")
        //  var  text1="kljkl"
        when (config.type) {

            PrinterType.BLUETOOTH -> {
                if (config.bluetoothAddress.isBlank()) {
                    onResult(false)
                    return
                }
                BluetoothPrinter.printText(
                    config.bluetoothAddress,
                    text,
                    onResult
                )
            }

            PrinterType.LAN -> {
                if (config.ip.isBlank()) {
                    onResult(false)
                    return
                }
                LanPrinter.printText(
                    config.ip,
                    config.port,
                    text,
                    onResult
                )
            }


            PrinterType.USB -> {

                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager

                val saved = prefs.getUSBPrinter(role)

                if (saved == null) {
                    Log.e("PRINT_NEW", "No saved USB printer")
                    onResult(false)
                    return
                }

                val (vendorId, productId) = saved

                val device = usbManager.deviceList.values.find {
                    it.vendorId == vendorId && it.productId == productId
                }

                if (device == null) {
                    Log.e("PRINT_NEW", "USB device not found")
                    onResult(false)
                    return
                }

                if (!usbManager.hasPermission(device)) {
                    Log.e("PRINT_NEW", "USB permission denied")
                    onResult(false)
                    return
                }

                // ✅ CORRECT CALL (with device)
                USBPrinter.printText(
                    context,
                    device,
                    text,
                    onResult
                )
            }

//            PrinterType.USB -> {
//                val device = config.usbDevice ?: run {
//                    onResult(false)
//                    return
//                }
//                USBPrinter.printText(
//                    text,
//                    onResult
//                )
//
//
//            }

            PrinterType.WIFI -> onResult(false)
        }
    }


    private fun loadSavedLogo(): Bitmap? {

        val logoFile = java.io.File(context.filesDir, "logo.png")

        return if (logoFile.exists()) {
            BitmapFactory.decodeFile(logoFile.absolutePath)
        } else null
    }


    private fun getOutletInfoOrNull(): OutletInfo? {
        val outletDao = AppDatabaseProvider.get(context).outletDao()
        val outletEntity = runBlocking { outletDao.getOutlet() }

        return if (outletEntity == null) {
            Log.e("PRINTER", "Outlet not configured")
            null
        } else {
            OutletMapper.fromEntity(outletEntity)
        }
    }








}//END OF CLASS
