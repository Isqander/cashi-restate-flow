@file:UseSerializers(BigDecimalSerializer::class)

package com.cashi.fee.model

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class TransactionRequest(
    val transactionId: String,
    val amount: BigDecimal,
    val asset: String,
    val assetType: String,
    val type: String,
    val state: String? = null,
    val createdAt: String? = null
)

@Serializable
data class FeeResponse(
    val transactionId: String,
    val amount: BigDecimal,
    val asset: String,
    val type: String,
    val fee: BigDecimal,
    val rate: BigDecimal,
    val description: String
)

@Serializable
data class FeeRecord(
    val id: String? = null,
    val transactionId: String,
    val amount: BigDecimal,
    val asset: String,
    val assetType: String,
    val transactionType: String,
    val fee: BigDecimal,
    val rate: BigDecimal,
    val description: String,
    val createdAt: String? = null
)

@Serializable
data class FeeHistoryResponse(
    val transactionId: String? = null,
    val records: List<FeeRecord>
)

enum class TransactionType(
    val displayName: String,
    val rate: BigDecimal,
    val feeDescription: String
) {
    MOBILE_TOP_UP("Mobile Top Up", BigDecimal("0.0015"), "Standard fee rate of 0.15%"),
    BANK_TRANSFER("Bank Transfer", BigDecimal("0.0010"), "Bank transfer fee of 0.10%"),
    CRYPTO_EXCHANGE("Crypto Exchange", BigDecimal("0.0030"), "Crypto exchange fee of 0.30%"),
    CARD_PAYMENT("Card Payment", BigDecimal("0.0020"), "Card payment fee of 0.20%");

    companion object {
        private val byDisplayName = entries.associateBy { it.displayName }

        fun fromDisplayName(name: String): TransactionType =
            byDisplayName[name]
                ?: throw IllegalArgumentException("Unknown transaction type: $name")
    }
}
