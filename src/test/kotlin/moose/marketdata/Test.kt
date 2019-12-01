package moose.marketdata

import com.nhaarman.mockitokotlin2.*
import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.Test
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGenerator{
    @Test
    fun testSymbolGeneration(){
        val four = Generator.genSymbol(4)
        assertEquals(four.length, 4)
        four.forEach { c -> assertTrue { (c in 'A'..'Z') || (c in 'a'..'z') } }
    }

    @Test
    fun testPriceGeneration(){
        val min = 100
        val max = 1000
        val price = Generator.genPrice(min, max)
        assertTrue { price in min..max }
    }

    @Test
    fun testTickersGeneration(){
        val tickers = Generator.genTickers(100)
        assertEquals(tickers.size, 100)
    }

    @Test
    fun testMarketData(){
        val endpoint:EndPoint = mock()
        Generator.start(100, 10, 1000, 10, 15, endpoint)
        Thread.sleep(50)
        Generator.stop()
        verify(endpoint, atLeastOnce()).onPriceUpdated(any(), any())
    }
}

class TestMarketDataPublisher{
    private val publisher : MarketDataPublisher = MarketDataPublisher()

    init{
        val mockVertx: Vertx = mock()
        val mockCtx: Context = mock()
        val mockEventBus: EventBus = mock()
        whenever(mockVertx.eventBus()).thenReturn(mockEventBus)
        publisher.init(mockVertx, mockCtx)
    }

    @Test
    fun testInitPaint(){
        val json = """
            {
            "ticker": "test1",
            "price": 100,
            "received_time": 1575202446703,
            "sent_time": 1575202446707
            }
        """.trimIndent()
        val payload = JsonObject(json)
        publisher.publishTick(payload, 1575202446707)

        val mockMsg : Message<JsonObject> = mock()
        publisher.initPaint(mockMsg, ZoneId.of("Asia/Tokyo"))
        val json2 = """
            {
            "ticker": "test1",
            "price": 100,
            "received_time": "2019-12-01 21:14:06.703",
            "sent_time": "2019-12-01 21:14:06.707" 
            }
        """.trimIndent()
        verify(mockMsg, times(1)).reply(eq(JsonArray().add(JsonObject(json2))))
    }
}