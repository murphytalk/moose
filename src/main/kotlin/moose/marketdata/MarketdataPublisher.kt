package moose.marketdata

import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import moose.Address
import moose.ErrorCodes
import moose.MarketDataAction
import moose.Timestamp
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MarketDataPublisher : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(MarketDataPublisher::class.java)
    }

    private val snapshot = mutableMapOf<String, JsonObject>()

    override fun start() {
        vertx.eventBus().consumer<JsonObject>(Address.marketdata_publisher.name) { m ->
            if ((MarketDataAction.action.name) !in m.headers()) {
                logger.error("No action header specified for message with headers {} and body {}",
                        m.headers(), m.body().encodePrettily())
                m.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal, "No action header specified")
                return@consumer
            }
            when (val action = m.headers()[MarketDataAction.action.name]) {
                MarketDataAction.tick.name ->
                    publishTick(m.body())
                MarketDataAction.init_paint.name ->
                    initPaint(m)
                else -> {
                    m.fail(ErrorCodes.BAD_ACTION.ordinal, "Bad action: $action")
                    logger.error("Unknown market data action {}", action)
                }
            }
        }
    }

    private fun publishTick(payload: JsonObject){
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

    private fun initPaint(m : Message<JsonObject>) {
        m.reply(JsonObject.mapFrom(snapshot))
    }
}

