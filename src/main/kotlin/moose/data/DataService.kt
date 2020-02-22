package moose.data

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.redis.client.Response
import moose.Address
import moose.marketdata.Generator
import moose.marketdata.Ticker
import moose.marketdata.TickerListCodec
import moose.marketdata.tickerListCodec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DataService : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(DataService::class.java)
    }

    private var tickers : List<Ticker>? = null
    private var redisSub: AbstractRedis? = null
    private var redisGet: AbstractRedis? = null

    override fun start(promise: Promise<Void>){
        // in the real world the list could be loaded from an external storage e.g. DB
        tickers = Generator.genTickers(config().getInteger("tickers"))

        vertx.eventBus().registerCodec(TickerListCodec())
                .consumer<String>(Address.data_service.name){ m ->
            m.reply(tickers, DeliveryOptions().setCodecName(tickerListCodec))
        }

        val redisConfig = config().getJsonObject("redis")
        val host = redisConfig.getString("hostname")
        val port = redisConfig.getInteger("port")
        redisSub = Redis(vertx, host, port, logger)

        Future.future<Void> {
            redisSub?.connect(it)
        }.compose {
            val getPromise = Promise.promise<Void>()
            redisGet = Redis(vertx, host, port, logger)
            redisGet?.connect(getPromise)
            getPromise.future()
        }.setHandler{
            if(it.succeeded()){
                redisSub?.setSubscriber("*",::handleSub)
                promise.complete()
            } else promise.fail(it.cause())
       }
    }

    private fun handleSub(res:Response){
        //res is an array : [pmessage, *, __keyspace@0__:jnkIu, hset]
        val key = res[2].toString().split(":")[1]
        redisGet?.api?.hgetall(key){
            if(it.succeeded()){
               logger.info(it.result()?.toString())
            }
            else logger.error("Failed to retrieve value for key $key")
        }
    }
}
