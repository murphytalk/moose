package moose.marketdata

import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import moose.Address
import moose.Timestamp
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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