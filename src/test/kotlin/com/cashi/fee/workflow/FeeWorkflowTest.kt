package com.cashi.fee.workflow

import com.cashi.fee.model.FeeRecord
import com.cashi.fee.model.TransactionRequest
import com.cashi.fee.repository.FeeRepository
import com.cashi.fee.service.FeeService
import com.cashi.fee.service.FeeServiceClient
import com.cashi.fee.strategy.FeeStrategyResolver
import dev.restate.client.Client
import dev.restate.sdk.testing.BindService
import dev.restate.sdk.testing.RestateClient
import dev.restate.sdk.testing.RestateTest
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@RestateTest
class FeeWorkflowTest {

    private val inMemoryRepo = InMemoryFeeRepository()
    private val resolver = FeeStrategyResolver.default()

    @BindService
    @Suppress("unused")
    val feeWorkflow = FeeWorkflow(inMemoryRepo, resolver)

    @BindService
    @Suppress("unused")
    val feeService = FeeService(inMemoryRepo)

    private suspend fun submitAndAwait(
        client: Client,
        key: String,
        req: TransactionRequest
    ) = FeeWorkflowClient.fromClient(client, key)
        .submit(req).attach().response()

    @Test
    @Timeout(10)
    fun `should calculate fee for Mobile Top Up`(@RestateClient client: Client) = runTest {
        val response = submitAndAwait(
            client, "txn_001",
            TransactionRequest(
                transactionId = "txn_001",
                amount = BigDecimal("1000"),
                asset = "USD",
                assetType = "FIAT",
                type = "Mobile Top Up",
                state = "SETTLED - PENDING FEE"
            )
        )

        assertEquals("txn_001", response.transactionId)
        assertEquals(0, response.fee.compareTo(BigDecimal("1.5")))
        assertEquals(0, response.rate.compareTo(BigDecimal("0.0015")))
        assertEquals(0, response.amount.compareTo(BigDecimal("1000")))
        assertTrue(response.description.contains("0.15%"))
    }

    @Test
    @Timeout(10)
    fun `should calculate fee for Bank Transfer`(@RestateClient client: Client) = runTest {
        val response = submitAndAwait(
            client, "txn_002",
            TransactionRequest(
                transactionId = "txn_002",
                amount = BigDecimal("5000"),
                asset = "USD",
                assetType = "FIAT",
                type = "Bank Transfer"
            )
        )

        assertEquals(0, response.fee.compareTo(BigDecimal("5.0")))
        assertEquals(0, response.rate.compareTo(BigDecimal("0.001")))
    }

    @Test
    @Timeout(10)
    fun `should calculate fee for Crypto Exchange`(@RestateClient client: Client) = runTest {
        val response = submitAndAwait(
            client, "txn_003",
            TransactionRequest(
                transactionId = "txn_003",
                amount = BigDecimal("2000"),
                asset = "BTC",
                assetType = "CRYPTO",
                type = "Crypto Exchange"
            )
        )

        assertEquals(0, response.fee.compareTo(BigDecimal("6.0")))
        assertEquals(0, response.rate.compareTo(BigDecimal("0.003")))
    }

    @Test
    @Timeout(10)
    fun `should persist fee record after workflow`(@RestateClient client: Client) = runTest {
        submitAndAwait(
            client, "txn_persist_001",
            TransactionRequest(
                transactionId = "txn_persist_001",
                amount = BigDecimal("1000"),
                asset = "USD",
                assetType = "FIAT",
                type = "Mobile Top Up"
            )
        )

        val records = inMemoryRepo.findByTransactionId("txn_persist_001")
        assertEquals(1, records.size)
        assertEquals(0, records[0].fee.compareTo(BigDecimal("1.5")))
    }

    @Test
    @Timeout(10)
    fun `should query fee by transaction ID via FeeService`(@RestateClient client: Client) = runTest {
        inMemoryRepo.save(
            FeeRecord(
                transactionId = "txn_query_001",
                amount = BigDecimal("1000"),
                asset = "USD",
                assetType = "FIAT",
                transactionType = "Mobile Top Up",
                fee = BigDecimal("1.5"),
                rate = BigDecimal("0.0015"),
                description = "Standard fee rate of 0.15%"
            )
        )

        val svcClient = FeeServiceClient.fromClient(client)
        val response = svcClient.byTransactionId("txn_query_001")

        assertEquals("txn_query_001", response.transactionId)
        assertEquals(1, response.records.size)
    }
}

class InMemoryFeeRepository : FeeRepository(DummyDataSource) {

    private val records = mutableListOf<FeeRecord>()

    override fun save(record: FeeRecord): Boolean {
        if (records.any { it.transactionId == record.transactionId }) return false
        records.add(record)
        return true
    }

    override fun findByTransactionId(transactionId: String) =
        records.filter { it.transactionId == transactionId }

    override fun findAll() = records.toList()
}

private object DummyDataSource : javax.sql.DataSource {
    override fun getConnection() = throw UnsupportedOperationException()
    override fun getConnection(u: String?, p: String?) = throw UnsupportedOperationException()
    override fun getLogWriter() = null
    override fun setLogWriter(out: java.io.PrintWriter?) {}
    override fun setLoginTimeout(seconds: Int) {}
    override fun getLoginTimeout() = 0
    override fun getParentLogger() = throw UnsupportedOperationException()
    override fun <T> unwrap(iface: Class<T>?) = throw UnsupportedOperationException()
    override fun isWrapperFor(iface: Class<*>?) = false
}
