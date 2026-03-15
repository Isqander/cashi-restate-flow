package com.cashi.fee.service

import com.cashi.fee.model.FeeHistoryResponse
import com.cashi.fee.repository.FeeRepository
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.runBlock

@Service
class FeeService(private val repository: FeeRepository) {

    @Handler
    suspend fun byTransactionId(ctx: Context, transactionId: String): FeeHistoryResponse {
        val records = ctx.runBlock("query-by-tx-id") {
            repository.findByTransactionId(transactionId)
        }
        return FeeHistoryResponse(transactionId = transactionId, records = records)
    }

    @Handler
    suspend fun allFees(ctx: Context): FeeHistoryResponse {
        val records = ctx.runBlock("query-all-fees") {
            repository.findAll()
        }
        return FeeHistoryResponse(records = records)
    }
}
