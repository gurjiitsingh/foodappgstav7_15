package com.it10x.foodappgstav7_15.utils

import com.it10x.foodappgstav7_15.data.online.models.OrderMasterData

fun OrderMasterData.createdAtMillis(): Long {
    return createdAt?.toDate()?.time ?: createdAtMillis
}