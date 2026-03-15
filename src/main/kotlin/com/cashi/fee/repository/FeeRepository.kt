package com.cashi.fee.repository

import com.cashi.fee.model.FeeRecord
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import javax.sql.DataSource

open class FeeRepository(private val dataSource: DataSource) {

    open fun save(record: FeeRecord): Boolean {
        val sql = """
            INSERT INTO fee_records (transaction_id, amount, asset, asset_type, transaction_type, fee, rate, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (transaction_id) DO NOTHING
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, record.transactionId)
                stmt.setBigDecimal(2, record.amount)
                stmt.setString(3, record.asset)
                stmt.setString(4, record.assetType)
                stmt.setString(5, record.transactionType)
                stmt.setBigDecimal(6, record.fee)
                stmt.setBigDecimal(7, record.rate)
                stmt.setString(8, record.description)
                return stmt.executeUpdate() > 0
            }
        }
    }

    open fun findByTransactionId(transactionId: String): List<FeeRecord> {
        val sql = "SELECT $COLUMNS FROM fee_records WHERE transaction_id = ? ORDER BY created_at"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, transactionId)
                return stmt.executeQuery().toRecords()
            }
        }
    }

    open fun findAll(): List<FeeRecord> {
        val sql = "SELECT $COLUMNS FROM fee_records ORDER BY created_at DESC"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                return stmt.executeQuery().toRecords()
            }
        }
    }

    private fun ResultSet.toRecords(): List<FeeRecord> {
        val list = mutableListOf<FeeRecord>()
        while (next()) list.add(toRecord())
        return list
    }

    private fun ResultSet.toRecord() = FeeRecord(
        id = getString("id"),
        transactionId = getString("transaction_id"),
        amount = getBigDecimal("amount"),
        asset = getString("asset"),
        assetType = getString("asset_type"),
        transactionType = getString("transaction_type"),
        fee = getBigDecimal("fee"),
        rate = getBigDecimal("rate"),
        description = getString("description"),
        createdAt = getString("created_at")
    )

    companion object {
        private const val COLUMNS =
            "id, transaction_id, amount, asset, asset_type, transaction_type, fee, rate, description, created_at"

        fun createDataSource(
            url: String = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/feeservice",
            user: String = System.getenv("DB_USER") ?: "feeservice",
            password: String = System.getenv("DB_PASSWORD") ?: "feeservice"
        ): HikariDataSource {
            val config = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 5
                minimumIdle = 2
                connectionTimeout = 5000
                idleTimeout = 300000
            }
            return HikariDataSource(config)
        }
    }
}
