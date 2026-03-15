@file:UseSerializers(BigDecimalSerializer::class)

package com.cashi.fee.strategy

import com.cashi.fee.model.BigDecimalSerializer
import com.cashi.fee.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class FeeCalculation(
    val fee: BigDecimal,
    val rate: BigDecimal,
    val description: String
)

interface FeeStrategy {
    val transactionType: TransactionType
    fun calculate(amount: BigDecimal): FeeCalculation
}

open class StandardFeeStrategy(override val transactionType: TransactionType) : FeeStrategy {
    override fun calculate(amount: BigDecimal) = FeeCalculation(
        fee = (amount * transactionType.rate).setScale(4, RoundingMode.HALF_UP),
        rate = transactionType.rate,
        description = transactionType.feeDescription
    )
}

class BankTransferFeeStrategy : StandardFeeStrategy(TransactionType.BANK_TRANSFER) {
    override fun calculate(amount: BigDecimal): FeeCalculation {
        val base = super.calculate(amount)
        return base.copy(
            fee = base.fee.coerceAtLeast(BigDecimal("0.5000")),
            description = "${transactionType.feeDescription} (min \$0.50)"
        )
    }
}

class FeeStrategyResolver(strategies: List<FeeStrategy>) {
    private val registry: Map<TransactionType, FeeStrategy> =
        strategies.associateBy { it.transactionType }

    fun resolve(displayName: String): FeeStrategy {
        val type = TransactionType.fromDisplayName(displayName)
        return registry[type]
            ?: throw IllegalArgumentException("No strategy registered for: $displayName")
    }

    companion object {
        fun default() = FeeStrategyResolver(
            TransactionType.entries.map { type ->
                when (type) {
                    TransactionType.BANK_TRANSFER -> BankTransferFeeStrategy()
                    else -> StandardFeeStrategy(type)
                }
            }
        )
    }
}
