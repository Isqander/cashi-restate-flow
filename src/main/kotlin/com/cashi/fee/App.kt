package com.cashi.fee

import com.cashi.fee.repository.FeeRepository
import com.cashi.fee.service.FeeService
import com.cashi.fee.strategy.FeeStrategyResolver
import com.cashi.fee.workflow.FeeWorkflow
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.endpoint.endpoint
import org.apache.logging.log4j.LogManager
import org.flywaydb.core.Flyway

private val logger = LogManager.getLogger("com.cashi.fee.App")

fun main() {
    val dataSource = FeeRepository.createDataSource()

    logger.info("Running database migrations...")
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    val repository = FeeRepository(dataSource)
    val strategyResolver = FeeStrategyResolver.default()

    logger.info("Starting Cashi Fee Service...")

    RestateHttpServer.listen(
        endpoint {
            bind(FeeWorkflow(repository, strategyResolver))
            bind(FeeService(repository))
        }
    )
}
