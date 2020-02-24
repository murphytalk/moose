package moose.data

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.unit.Async
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.redis.client.RedisOptions
import moose.marketdata.MarketData
import moose.marketdata.MarketDataPayload
import moose.marketdata.Ticker
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

@RunWith(VertxUnitRunner::class) @Ignore
class TestRedis{
    @Rule
    //to get rid of the "must be public" error
    //see https://proandroiddev.com/fix-kotlin-and-new-activitytestrule-the-rule-must-be-public-f0c5c583a865
    @JvmField
    val rule = RunTestOnContext()

    private class V(private val redis : KotlinRedis, private val async: Async, private val logger: Logger) : AbstractVerticle(){
        override fun start(promise: Promise<Void>) {
            Future.future<Void>{
                redis.connect(it)
            }.setHandler{
                if(it.succeeded()){
                    thread(start = true, name = "redis-test-thread") {
                            logger.info("start pub to redis")
                            for (i in 1..repeatCount)
                                redis.publish(data)
                            logger.info("done pub to redis")
                            async.complete()
                    }
                    promise.complete()
                }
                else promise.fail(it.cause())
            }
        }
    }
    @Test
    fun testKotlinImplementation(ctx: TestContext){
        val async = ctx.async()
        val v = V(KotlinRedis(rule.vertx(), host, port,RedisOptions().setMaxWaitingHandlers(repeatCount)), async,  logger)
        val vertx = rule.vertx()
        vertx.deployVerticle(v)
        logger.info("verticle deployed")
        logger.info("lock notified")
        async.awaitSuccess()
        logger.info("done")
    }

    @Test
    fun testJavaImplementation(ctx: TestContext){
        val async = ctx.async()
        val v = V(Redis(rule.vertx(), host, port, RedisOptions().setMaxWaitingHandlers(repeatCount),null), async,  logger)
        val vertx = rule.vertx()
        vertx.deployVerticle(v)
        logger.info("verticle deployed")
        logger.info("lock notified")
        async.awaitSuccess()
        logger.info("done")
    }

    @After
    fun finish(ctx: TestContext) {
        val vertx = rule.vertx()
        vertx.close(ctx.asyncAssertSuccess())
    }


    companion object {
        private const val host = "localhost"
        private const val port = 6379
        private val data = MarketData(Ticker("ticker"), MarketDataPayload(100))
        private const val repeatCount = 1000000
        private val logger: Logger = LoggerFactory.getLogger(TestRedis::class.java)
    }
}