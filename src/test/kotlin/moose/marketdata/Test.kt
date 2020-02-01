package moose.marketdata

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nhaarman.mockitokotlin2.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.Address
import moose.MarketDataAction
import moose.Timestamp
import moose.http.HttpServerVerticle
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGenerator {
    @Test
    fun testSymbolGeneration() {
        val four = Generator.genSymbol(4)
        assertEquals(four.length, 4)
        four.forEach { c -> assertTrue { (c in 'A'..'Z') || (c in 'a'..'z') } }
    }

    @Test
    fun testPriceGeneration() {
        val min = 100
        val max = 1000
        val price = Generator.genPrice(min, max)
        assertTrue { price in min..max }
    }

    @Test
    fun testTickersGeneration() {
        val tickers = Generator.genTickers(100)
        assertEquals(tickers.size, 100)
    }

    @Test
    fun testMarketData() {
        val endpoint: EndPoint = mock()
        Generator.start(Generator.genTickers(100), 10, 1000, 10, 15, endpoint)
        Thread.sleep(50)
        Generator.stop()
        verify(endpoint, atLeastOnce()).onPriceUpdated(any(), any())
    }
}

@RunWith(VertxUnitRunner::class)
class TestMarketDataPublisher {
    private val ticker = "test1"
    private val price = 100
    private val receivedTime = 1575202446703

    @Rule
    //to get rid of the "must be public" error
    //see https://proandroiddev.com/fix-kotlin-and-new-activitytestrule-the-rule-must-be-public-f0c5c583a865
    @JvmField
    val rule = RunTestOnContext()

    @Before
    fun prepare(ctx: TestContext) {
        // to encode Kotlin data class
        // https://github.com/vert-x3/vertx-lang-kotlin/issues/43
        DatabindCodec.mapper().registerModule(KotlinModule())
    }

    @After
    fun finish(ctx: TestContext) {
        val vertx = rule.vertx()
        vertx.close(ctx.asyncAssertSuccess())
    }

    private fun publishTick(vertx: Vertx){
        val msg = MarketData(Ticker(ticker), MarketDataPayload(price, receivedTime))
        vertx.eventBus().send(
                Address.marketdata_publisher.name,
                //JsonObject.mapFrom(msg),
                msg,
                DeliveryOptions().addHeader(MarketDataAction.action.name, MarketDataAction.tick.name))
    }

    @Test
    fun testMarketDataPublish(ctx: TestContext){
        val async = ctx.async()
        val vertx = rule.vertx()

        vertx.deployVerticle(object : MarketDataPublisher(){
            override fun publishTick(md: MarketData){
                assertThat(MarketData(Ticker(ticker), MarketDataPayload(price, receivedTime)), `is`(md))
                async.complete()
            }
        }, DeploymentOptions()){
            publishTick(vertx)
        }
    }

    //@Test
    fun testMarketStatusUpdate(ctx: TestContext) {
        val async = ctx.async()
        val vertx = rule.vertx()

        // Simulate the browser side : subscribe to market data status
        class V : AbstractVerticle() {
            override fun start() {
                vertx.eventBus().consumer<JsonObject>(Address.marketdata_status.name) {
                    val returned = it.body()

                    val json = json {
                        obj(
                                "ticker" to obj(
                                        "name" to ticker
                                ),
                                "payload" to obj(
                                        "price" to price,
                                        "receivedTime" to Timestamp.formatEpoch(receivedTime)
                                )
                        )
                    }

                    // don't compare publish time as it is populated with time when test is executed
                    returned.map.remove("publishTime")
                    assertEquals(returned, json)
                    async.complete()
                }
            }
        }
        vertx.deployVerticle(V(), DeploymentOptions()){ar ->
            assertTrue { ar.succeeded() }
            publishTick(vertx)
        }

        async.awaitSuccess(5000)
    }

    @Test
    fun testInitPaint(ctx: TestContext) {
        val async = ctx.async()
        val vertx = rule.vertx()

        vertx.deployVerticle(MarketDataPublisher(), DeploymentOptions())

        val httpConfig = json{
            obj(
                    "port" to 12345
            )
        }
        vertx.deployVerticle(HttpServerVerticle(),DeploymentOptions().setConfig(httpConfig)) { ar ->
            assertTrue(ar.succeeded())
            val client = WebClient.create(vertx)
            client.get(httpConfig.getInteger("port"), "localhost","/api/md/initpaint").send {
                assertTrue { it.succeeded() }
                val res = it.result().body()
                val returned = JsonArray(res)
                val json = json{
                    array(
                            obj(
                                    "ticker" to obj(
                                            "name" to ticker
                                    ),
                                    "payload" to obj(
                                            "price" to price,
                                            "receivedTime" to Timestamp.formatEpoch(receivedTime)
                                    )
                            )
                    )
                }

                // don't compare publish time as it is populated with time when test is executed
                returned.getJsonObject(0).map.remove("publishTime")
                assertEquals(returned, json)
                async.complete()
            }
            publishTick(vertx)
        }
    }
}

class TestCustCodec{
    @Before
    fun prepare(){
        DatabindCodec.mapper().registerModule(KotlinModule())
    }

    @Test
    fun testMarketDataCodec(){
        val md = MarketData(Ticker("test"), MarketDataPayload(100, 123),456)
        val codec = MarketDataCodec()
        val buffer = Buffer.buffer()
        codec.encodeToWire(buffer, md)
        assertThat(codec.decodeFromWire(0, buffer), `is`(md))
    }
}