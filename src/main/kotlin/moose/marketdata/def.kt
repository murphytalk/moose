package moose.marketdata

data class Ticker(val name: String)

interface EndPoint{
    fun onPriceUpdated(ticker: Ticker, price: Int)
}