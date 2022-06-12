package moose.marketdata

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.RedisOptions
import moose.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneId

class MarketDataPublisher : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(MarketDataPublisher::class.java)
    }

    private val snapshot = mutableMapOf<String, MarketData>()
    private var redis: Redis? = null

    // use data class ?
    // https://github.com/vert-x3/vertx-lang-kotlin/issues/43
    override fun start(promise: Promise<Void>) {
        vertx.eventBus().registerDefaultCodec(MarketData::class.java, MarketDataCodec())
        .consumer<MarketData>(AddressMarketDataPublisher) { m ->
            if ((MarketDataActionAction) !in m.headers()) {
                logger.error("No action header specified for message with headers {} and body {}",
                        m.headers(), m.body())
                m.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal, "No action header specified")
                return@consumer
            }
            when (val action = m.headers()[MarketDataActionAction]) {
                MarketDataActionTick -> {
                    val md = m.body()
                    snapshot[md.ticker.name] = md
                    publishTick(md)
                }
                MarketDataActionInitPaint ->
                    initPaint(m)
                else -> {
                    m.fail(ErrorCodes.BAD_ACTION.ordinal, "Bad action: $action")
                    logger.error("Unknown market data action {}", action)
                }
            }
        }
        if(redis == null) {
            redis = Redis(vertx, config().getString("hostname"), config().getInteger("port"), RedisOptions(), logger)
            redis?.connect(promise)
        }
        else promise.complete()
    }

    private fun marketDataToJson(marketData:MarketData, zone: ZoneId): JsonObject{
        fun formatEpoch(tick: JsonObject, field: String){
            tick.put(field,  Timestamp.formatEpoch(tick.getLong(field), zone))
        }
        val md = JsonObject.mapFrom(marketData)
        formatEpoch(md, "publishTime")
        formatEpoch(md.getJsonObject("payload"), "receivedTime")
        return md
    }

    private fun publishTick(marketData: MarketData){
        marketData.publishTime = System.currentTimeMillis()
        redis?.publish(marketData)
        //vertx.eventBus().publish(Address_marketdata_status, marketDataToJson(marketData, ZoneId.systemDefault()))
    }

    private fun initPaint(m : Message<MarketData>, zone:ZoneId = ZoneId.systemDefault()) {
        m.reply(JsonArray(snapshot.map {marketDataToJson(it.value, zone)}))
    }

    override fun stop() {
        redis?.disconnect()
    }
}

