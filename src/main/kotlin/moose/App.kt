package moose

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.marketdata.EndPoint
import moose.marketdata.Generator
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

object Timestamp {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    fun formatEpoch(epochMillis: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(formatter)
}

class MarketDataPublisher : AbstractVerticle() {
    private companion object{
        val logger: Logger = LoggerFactory.getLogger(MarketDataPublisher::class.java)
    }
    private val snapshot = mutableMapOf<String, JsonObject>()
    override fun start() {
        vertx.eventBus().consumer<JsonObject>(Address.marketdata_publisher.name) { m ->
            val payload = m.body()
            payload.put("sent_time", System.currentTimeMillis())
            snapshot[payload.getString("ticker")] = payload
            vertx.eventBus().publish(Address.marketdata_status.name, payload)
            logger.debug(
                    "Ticker {}, price={}, received at {}, published at {}",
                    payload.getString("ticker"),
                    payload.getInteger("price"),
                    Timestamp.formatEpoch(payload.getLong("received_time")),
                    Timestamp.formatEpoch(payload.getLong("sent_time"))
            )
        }
    }
}

class MainVerticle : AbstractVerticle() {
    inner class MarketDataEndpoint : EndPoint {
        override fun onPriceUpdated(ticker: Ticker, price: Int) {
            val msg = json {
                obj(
                        "ticker" to ticker.name,
                        "price" to price,
                        "received_time" to System.currentTimeMillis()
                )
            }
            vertx.eventBus().send(Address.marketdata_publisher.name, msg)
        }
    }

    override fun start(promise: Promise<Void>) {
        val publisherDeployment = Promise.promise<String>()
        vertx.deployVerticle(MarketDataPublisher(), publisherDeployment)
        publisherDeployment.future().setHandler { ar ->
            if (ar.succeeded()) {
                Generator.start(100, 10, 1000, 10, 1000, MarketDataEndpoint())
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
    }
}
