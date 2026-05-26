package com.it10x.foodappgstav7_15.printer

import com.it10x.foodappgstav7_15.data.pos.entities.PosOrderItemEntity
import com.it10x.foodappgstav7_15.data.pos.entities.PosOrderMasterEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.String

object PrintOrderBuilder {

    // -------------------------
    // BUILD PRINT ORDER
    // -------------------------
    fun build(
        master: PosOrderMasterEntity,
        items: List<PosOrderItemEntity>
    ): PrintOrder {

        val printItems = items.map { item ->
            PrintItem(
                name = item.name,
                quantity = item.quantity,
                price = item.basePrice,
                subtotal = item.itemSubtotal,
                note = item.note ?: "",
                modifiersJson = item.modifiersJson ?: ""
            )
        }

        return PrintOrder(

            // ---------- CORE ----------
            orderNo = master.srno.toString(),
            dateTime = master.createdAt.formatMillis(),

            // ---------- ORDER TYPE ----------
            orderType = master.orderType,
            tableNo = master.tableNo,
            paymentMode = master.paymentMode,


            // ---------- DELIVERY ----------
            customerName = master.customerName?:"Walk-in",
            customerPhone = master.customerPhone,
            dAddressLine1 = master.dAddressLine1,
            dAddressLine2 = master.dAddressLine2,
            dCity = master.dCity,
            dLandmark = master.dLandmark,
            dState = null,
            dZipcode = null,


            // ---------- ITEMS ----------
            items = printItems,

            // ---------- TOTALS ----------
            itemTotal = master.itemTotal,
            deliveryFee = master.deliveryFee,
            tax = master.taxTotal,
            discount = master.discountTotal,
            grandTotal = master.grandTotal
        )
    }



    // -------------------------
    // DATE FORMAT
    // -------------------------
    private fun Long.formatMillis(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        return sdf.format(Date(this))
    }
}
