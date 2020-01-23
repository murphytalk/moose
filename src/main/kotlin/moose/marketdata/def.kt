package moose.marketdata

data class Ticker(val name: String)
data class MarketDataPayload(val price:Int, val receivedTime: Long)
data class MarketData(val ticker: Ticker, val payload:MarketDataPayload, var publishTime:Long = 0)

interface EndPoint{
    fun onPriceUpdated(ticker: Ticker, price: Int)
}
