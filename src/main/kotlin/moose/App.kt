package moose

import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.marketdata.EndPoint
import moose.marketdata.Generator
import moose.marketdata.MarketDataPublisher
import moose.marketdata.Ticker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Address {
    marketdata_publisher,
    marketdata_status
}

enum class MarketDataAction {
    action,
    tick,
    init_paint
}

enum class ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
}

object Timestamp {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    fun formatEpoch(epochMillis: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(formatter)
}

class MainVerticle : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    }

    inner class MarketDataEndpoint : EndPoint {
        override fun onPriceUpdated(ticker: Ticker, price: Int) {
            val msg = json {
                obj(
                        "ticker" to ticker.name,
                        "price" to price,
                        "received_time" to System.currentTimeMillis()
                )
            }
            vertx.eventBus().send(
                Address.marketdata_publisher.name,
                msg,
                DeliveryOptions().addHeader(MarketDataAction.action.name, MarketDataAction.tick.name)
            )
        }
    }

    override fun start(promise: Promise<Void>) {
        val publisherDeployment = Promise.promise<String>()
        vertx.deployVerticle(MarketDataPublisher(), publisherDeployment)

        publisherDeployment.future().compose { _ ->
            val httpDeployment = Promise.promise<String>()
            vertx.deployVerticle(
                    "moose.http.HttpServerVerticle",
                    DeploymentOptions().setInstances(2),
                    httpDeployment)
            httpDeployment.future()
        }.setHandler { ar ->
            if (ar.succeeded()) {
                Generator.start(100, 10, 1000, 10, 1000, MarketDataEndpoint())
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
    }

    override fun stop() {
        logger.info("Stopping market data thread")
        Generator.stop()
        logger.info("Market data thread stopped")
    }
}
