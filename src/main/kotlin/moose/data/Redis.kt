package moose.data

import io.vertx.core.*
import io.vertx.core.net.SocketAddress
import io.vertx.redis.client.*
import io.vertx.redis.client.Redis
import moose.http.HttpServerVerticle
import moose.marketdata.MarketData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.pow

const val MAX_RECONNECT_RETRIES = 10

// see the Java class that extends this for the reason
open class KotlinRedis (private val vertx: Vertx, hostname: String?, port :Int?, additionalRedisOpt : RedisOptions = RedisOptions()){
    private companion object{
        val logger: Logger = LoggerFactory.getLogger(KotlinRedis::class.java)
    }
    private lateinit var redis: Redis
    lateinit var api: RedisAPI
    private val redisOptions : RedisOptions = if (port==null) RedisOptions(additionalRedisOpt) else RedisOptions(additionalRedisOpt).setConnectionString("$hostname:$port")

    fun setSubscriber(keyPattern :String, handler: (Response) -> Unit){
        api.psubscribe(listOf(keyPattern)){
            if(it.succeeded()){
                logger.debug("Subscribed {}",it.result())
                handler(it.result())
            }
            else{
                logger.error("Failed to subscribe pattern $keyPattern")
            }
        }
    }

    fun connect(promise: Promise<Void>) {
        if (redisOptions.endpoints == null){
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

    fun disconnect(){
        redis.close()
    }

    open fun publish(marketData: MarketData){
       // see the Java version
       api.hmset(listOf(
                marketData.ticker.name,
                "price", marketData.payload.price.toString(),
                "received_time", marketData.payload.receivedTime.toString(),
                "published_time", marketData.publishTime.toString()
        )) {
            if(it.succeeded()) {
                logger.debug("Saved to Redis: {}",marketData)
            }
            else{
                logger.error("Failed save to Redis {}",it.cause().message)
            }
        }
    }

    private fun createRedisClient(handler: (AsyncResult<RedisConnection>) -> Unit){
        logger.info("Connecting to Redis ${redisOptions.endpoint}")
        redis = Redis.createClient(vertx, redisOptions)
        redis.connect()
            .onComplete{
                if(it.succeeded()) {
                    logger.info("Connected to Redis ${redisOptions.endpoint}")
                    val exceptionHandler: (Throwable) -> Unit = { e ->
                        logger.error("Redis exception {}", e.message)
                        attemptReconnect(0)
                    }
                    val c = it.result()
                    c.exceptionHandler(exceptionHandler).endHandler { attemptReconnect(0) }
                    api = RedisAPI.api(c)
                    handler(it)
                }
            }
    }

    private fun attemptReconnect(retry:Int){
        if (retry > MAX_RECONNECT_RETRIES){
            logger.warn("Retried reconnect to Redis $MAX_RECONNECT_RETRIES times, giving up")
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