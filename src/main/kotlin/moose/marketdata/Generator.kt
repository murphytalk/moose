package moose.marketdata

import java.lang.StringBuilder
import java.util.Random
import kotlin.concurrent.thread


object Generator {
    private val random = Random()
    private var th : Thread? = null

    @Volatile private var stop = false

    internal fun genSymbol(len:Int) : String{
        fun c() : Char{
            val lowerCase = random.nextBoolean()
            val from = if (lowerCase) 'a'.toInt() else 'A'.toInt()
            return (from + random.nextInt(26)).toChar()
        }

        val s = StringBuilder()
        (0 until len).forEach { _ -> s.append(c())}
        return s.toString()
    }

    internal fun genPrice(min:Int, max:Int) : Int{
        return min + random.nextInt(max - min + 1)
    }

    internal fun genTickers(numOfTickers: Int) : List<Ticker>{
        val tickers =mutableSetOf<Ticker>()
        while (tickers.size < numOfTickers){
            val ticker = Ticker(genSymbol(4 + random.nextInt(3)))
            if (ticker !in tickers){
                tickers.add(ticker)
            }
        }
        return tickers.map { it }
    }

    fun start(numOfTickers: Int, minPrice: Int, maxPrice:Int, intervalMinMs:Int, intervalMaxMs: Int, endPoint: EndPoint){
        val tickers = genTickers(numOfTickers)
        th = thread(start = true, name = "market-data-gen-thread") {
            val intervalRange = intervalMaxMs - intervalMinMs + 1
            do{
                val ticker = tickers[random.nextInt(tickers.size)]
                endPoint.onPriceUpdated(ticker, genPrice(minPrice, maxPrice))
                val msUntilNextPriceUpdate = intervalMinMs + random.nextInt(intervalRange)
                Thread.sleep(msUntilNextPriceUpdate.toLong())
            }while (!stop)
        }
   }

   fun stop(){
       stop = true
       th?.join()
   }
}