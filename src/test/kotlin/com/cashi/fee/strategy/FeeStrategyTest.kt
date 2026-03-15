package com.cashi.fee.strategy

import com.cashi.fee.model.TransactionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal

class FeeStrategyTest : FeatureSpec({

    val resolver = FeeStrategyResolver.default()

    feature("Mobile Top Up fee calculation") {
        scenario("calculates 0.15% fee on 1000 USD") {
            val result = StandardFeeStrategy(TransactionType.MOBILE_TOP_UP).calculate(BigDecimal("1000"))
            result.fee.compareTo(BigDecimal("1.5")) shouldBe 0
            result.rate.compareTo(BigDecimal("0.0015")) shouldBe 0
            result.description shouldContain "0.15%"
        }

        scenario("calculates fee on small amount") {
            val result = StandardFeeStrategy(TransactionType.MOBILE_TOP_UP).calculate(BigDecimal("10"))
            result.fee.compareTo(BigDecimal("0.015")) shouldBe 0
        }
    }

    feature("Bank Transfer fee calculation") {
        scenario("calculates 0.10% fee on 5000 USD") {
            val result = BankTransferFeeStrategy().calculate(BigDecimal("5000"))
            result.fee.compareTo(BigDecimal("5.0")) shouldBe 0
            result.rate.compareTo(BigDecimal("0.001")) shouldBe 0
        }

        scenario("applies minimum fee of 0.50 on small amounts") {
            val result = BankTransferFeeStrategy().calculate(BigDecimal("100"))
            result.fee.compareTo(BigDecimal("0.50")) shouldBe 0
            result.description shouldContain "min"
        }
    }

    feature("Crypto Exchange fee calculation") {
        scenario("calculates 0.30% fee on 2000 USD") {
            val result = StandardFeeStrategy(TransactionType.CRYPTO_EXCHANGE).calculate(BigDecimal("2000"))
            result.fee.compareTo(BigDecimal("6.0")) shouldBe 0
            result.rate.compareTo(BigDecimal("0.003")) shouldBe 0
        }
    }

    feature("Card Payment fee calculation") {
        scenario("calculates 0.20% fee on 500 USD") {
            val result = StandardFeeStrategy(TransactionType.CARD_PAYMENT).calculate(BigDecimal("500"))
            result.fee.compareTo(BigDecimal("1.0")) shouldBe 0
            result.rate.compareTo(BigDecimal("0.002")) shouldBe 0
        }
    }

    feature("Fee strategy resolution") {
        scenario("resolves Mobile Top Up by display name") {
            val strategy = resolver.resolve("Mobile Top Up")
            strategy.transactionType.displayName shouldBe "Mobile Top Up"
        }

        scenario("resolves Bank Transfer by display name") {
            val strategy = resolver.resolve("Bank Transfer")
            strategy.transactionType.displayName shouldBe "Bank Transfer"
        }

        scenario("resolves all registered types") {
            listOf("Mobile Top Up", "Bank Transfer", "Crypto Exchange", "Card Payment").forEach {
                resolver.resolve(it)
            }
        }

        scenario("throws for unknown transaction type") {
            shouldThrow<IllegalArgumentException> {
                resolver.resolve("Unknown Type")
            }
        }
    }

    feature("End-to-end fee calculation through resolver") {
        scenario("Mobile Top Up 1000 USD produces fee 1.5 with rate 0.0015") {
            val strategy = resolver.resolve("Mobile Top Up")
            val result = strategy.calculate(BigDecimal("1000"))
            result.fee.compareTo(BigDecimal("1.5")) shouldBe 0
            result.rate.compareTo(BigDecimal("0.0015")) shouldBe 0
            result.description shouldContain "0.15%"
        }
    }
})
