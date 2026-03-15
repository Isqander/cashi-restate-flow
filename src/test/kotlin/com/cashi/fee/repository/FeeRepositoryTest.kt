package com.cashi.fee.repository

import com.cashi.fee.model.FeeRecord
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

class FeeRepositoryTest : FeatureSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
        withDatabaseName("feeservice_test")
        withUsername("test")
        withPassword("test")
    }

    lateinit var repository: FeeRepository

    beforeSpec {
        postgres.start()
        val dataSource = FeeRepository.createDataSource(
            url = postgres.jdbcUrl,
            user = postgres.username,
            password = postgres.password
        )
        Flyway.configure().dataSource(dataSource).load().migrate()
        repository = FeeRepository(dataSource)
    }

    afterSpec {
        postgres.stop()
    }

    feature("Saving fee records") {
        scenario("saves a new fee record") {
            val record = feeRecord("txn_save_001")
            val saved = repository.save(record)
            saved shouldBe true

            val found = repository.findByTransactionId("txn_save_001").firstOrNull()
            found shouldNotBe null
            found!!.fee.compareTo(BigDecimal("1.5")) shouldBe 0
        }

        scenario("idempotent insert does not duplicate") {
            val record = feeRecord("txn_idem_001")
            repository.save(record) shouldBe true
            repository.save(record) shouldBe false

            repository.findByTransactionId("txn_idem_001") shouldHaveSize 1
        }
    }

    feature("Querying fee records") {
        scenario("finds records by transaction ID") {
            val txId = "txn_query_001"
            repository.save(feeRecord(txId))
            val records = repository.findByTransactionId(txId)
            records shouldHaveSize 1
            records[0].transactionId shouldBe txId
        }

        scenario("returns empty list for unknown transaction") {
            repository.findByTransactionId("txn_nonexistent") shouldHaveSize 0
        }
    }
})

private fun feeRecord(txId: String, type: String = "Mobile Top Up") = FeeRecord(
    transactionId = txId,
    amount = BigDecimal("1000.0000"),
    asset = "USD",
    assetType = "FIAT",
    transactionType = type,
    fee = BigDecimal("1.5000"),
    rate = BigDecimal("0.0015"),
    description = "Standard fee rate of 0.15%"
)
