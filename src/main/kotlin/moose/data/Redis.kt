package moose.data

import io.vertx.core.*
import io.vertx.core.net.SocketAddress
import io.vertx.kotlin.redis.client.hmsetAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import io.vertx.redis.client.Response
import moose.marketdata.MarketData
import org.slf4j.Logger
import kotlin.math.max
import kotlin.math.pow

const val MAX_RECONNECT_RETRIES = 10

class Redis (val vertx: Vertx, val hostname: String?, val port :Int?, val logger: Logger? = null){
    private var redis: Redis? = null
    var api: RedisAPI? = null

    fun setSubscriber(keyPattern :String, handler: (Response) -> Unit){
        api?.psubscribe(listOf(keyPattern)){
            if(it.succeeded()){
                logger?.debug("Subscribed {}",it.result())
                redis?.handler(handler)
            }
            else{
                logger?.error("Failed to subscribe pattern $keyPattern")
            }
        }
    }

    fun connect(promise: Promise<Void>) {
        if ( port == null || hostname == null){
            promise.complete()
            return
        }
        createRedisClient{
            if(it.succeeded()){
                promise.complete()
            }
            else{
                promise.fail(it.cause())
            }
        }
    }

    fun publish(marketData: MarketData){
        api?.hmset(listOf(
                marketData.ticker.name,
                "price", marketData.payload.price.toString(),
                "received_time", marketData.payload.receivedTime.toString(),
                "published_time", marketData.publishTime.toString()
        )){
            if(it.succeeded()) {
                logger?.debug("Saved to Redis: {}",marketData)
            }
            else{
                logger?.error("Failed save to Redis {}",it.cause().message)
            }
        }
    }

    private fun createRedisClient(handler: (AsyncResult<Redis>) -> Unit){
        logger?.info("Connecting to Redis @ $hostname:$port")
        redis = Redis.createClient(vertx, RedisOptions().setEndpoint(SocketAddress.inetSocketAddress(port!!, hostname)))
        redis?.connect{
            if(it.succeeded()){
                logger?.info("Connected to Redis @ $hostname:$port")
                val c = it.result()
                val exceptionHandler: (Throwable) -> Unit = { e ->
                    logger?.error("Redis exception {}", e.message)
                    attemptReconnect(0)
                }
                c.exceptionHandler(exceptionHandler).endHandler{attemptReconnect(0)}
                api = RedisAPI.api(c)
            }
            handler(it)
        }
    }

    private fun attemptReconnect(retry:Int){
        if (retry > MAX_RECONNECT_RETRIES){
            logger?.warn("Retried reconnect to Redis $MAX_RECONNECT_RETRIES times, giving up")
        } else{
            val backoff = (2.toDouble().pow(MAX_RECONNECT_RETRIES - max(MAX_RECONNECT_RETRIES - retry, 9).toDouble()) * 10).toLong()
            vertx.setTimer(backoff){ _ ->
                createRedisClient {
                    if(it.failed()){
                        attemptReconnect(retry + 1)
                    }
                }
            }
        }
    }
}