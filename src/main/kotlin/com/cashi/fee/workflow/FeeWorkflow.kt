package com.cashi.fee.workflow

import com.cashi.fee.model.FeeRecord
import com.cashi.fee.model.FeeResponse
import com.cashi.fee.model.TransactionRequest
import com.cashi.fee.repository.FeeRepository
import com.cashi.fee.strategy.FeeStrategyResolver
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.kotlin.SharedWorkflowContext
import dev.restate.sdk.kotlin.WorkflowContext
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.kotlin.stateKey
import java.math.BigDecimal
import org.apache.logging.log4j.LogManager

@Workflow
class FeeWorkflow(
    private val repository: FeeRepository,
    private val resolver: FeeStrategyResolver
) {
    companion object {
        private val STATUS = stateKey<String>("status")
        private val logger = LogManager.getLogger(FeeWorkflow::class.java)

        private object Status {
            const val CALCULATING = "CALCULATING"
            const val CHARGING = "CHARGING"
            const val RECORDING = "RECORDING"
            const val SETTLED = "SETTLED - FEE APPLIED"
            const val UNKNOWN = "UNKNOWN"
        }
    }

    @Workflow
    suspend fun run(ctx: WorkflowContext, req: TransactionRequest): FeeResponse {
        val txId = ctx.key()
        logger.info("Starting fee workflow for transaction {}", txId)

        if (req.transactionId != txId) {
            throw TerminalException("Payload transactionId '${req.transactionId}' does not match workflow key '$txId'")
        }

        if (req.amount <= BigDecimal.ZERO) {
            throw TerminalException("Amount must be positive, got: ${req.amount}")
        }

        ctx.set(STATUS, Status.CALCULATING)
        val calc = ctx.runBlock("calculate-fee") {
            logger.info("Calculating fee for {} (type={})", txId, req.type)
            resolver.resolve(req.type).calculate(req.amount)
        }

        ctx.set(STATUS, Status.CHARGING)
        ctx.runBlock("charge-fee") {
            logger.info("Charging fee {} {} for transaction {}", calc.fee, req.asset, txId)
        }

        ctx.set(STATUS, Status.RECORDING)
        ctx.runBlock("record-fee") {
            val record = FeeRecord(
                transactionId = txId,
                amount = req.amount,
                asset = req.asset,
                assetType = req.assetType,
                transactionType = req.type,
                fee = calc.fee,
                rate = calc.rate,
                description = calc.description
            )
            val saved = repository.save(record)
            if (saved) logger.info("Fee record saved for transaction {}", txId)
            else logger.info("Duplicate fee record skipped for transaction {}", txId)
        }

        ctx.set(STATUS, Status.SETTLED)
        return FeeResponse(
            transactionId = txId,
            amount = req.amount,
            asset = req.asset,
            type = req.type,
            fee = calc.fee,
            rate = calc.rate,
            description = calc.description
        )
    }

    @Shared
    suspend fun getStatus(ctx: SharedWorkflowContext): String {
        return ctx.get(STATUS) ?: Status.UNKNOWN
    }
}
