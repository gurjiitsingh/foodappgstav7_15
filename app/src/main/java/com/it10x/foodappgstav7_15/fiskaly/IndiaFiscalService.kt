package com.it10x.foodappgstav7_15.fiskaly

import com.it10x.foodappgstav7_15.data.pos.entities.PosKotItemEntity
import com.it10x.foodappgstav7_15.fiscal.FiscalContext
import com.it10x.foodappgstav7_15.fiscal.FiscalService
import com.it10x.foodappgstav7_15.ui.payment.PaymentInput

class IndiaFiscalService : FiscalService {

    override suspend fun start(): FiscalContext {
      //  return FiscalContext(null, null)
        return FiscalContext("IN", "NOOP")
    }

    override suspend fun finish(
        context: FiscalContext,
        payments: List<PaymentInput>,
        items: List<PosKotItemEntity>
    ) {
        // ✅ Nothing for India
    }

    override suspend fun cancel(context: FiscalContext) {
        // ✅ Nothing
    }
}