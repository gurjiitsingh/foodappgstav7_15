package com.it10x.foodappgstav7_15.data.printqueue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.it10x.foodappgstav7_15.data.printqueue.PrintQueueEntity

@Dao
interface PrintQueueDao {

    @Insert
    suspend fun insert(job: PrintQueueEntity)

    @Query("SELECT * FROM print_queue WHERE status='PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PrintQueueEntity>

    @Query("UPDATE print_queue SET status=:status, retryCount=:retry WHERE id=:id")
    suspend fun updateStatus(id: String, status: String, retry: Int)

    @Query("DELETE FROM print_queue WHERE id=:id")
    suspend fun delete(id: String)
}