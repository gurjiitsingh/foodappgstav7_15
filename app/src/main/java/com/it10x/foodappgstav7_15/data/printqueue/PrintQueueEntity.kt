package com.it10x.foodappgstav7_15.data.printqueue

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_queue")
data class PrintQueueEntity(

    @PrimaryKey
    val id: String,

    val role: String,
    val text: String,

    val paymentMode: String? = null,
    val grandTotal: Double? = null,

    val status: String, // PENDING, PRINTING, FAILED
    val retryCount: Int,

    val createdAt: Long
)