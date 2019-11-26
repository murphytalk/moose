package moose.marketdata

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test
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