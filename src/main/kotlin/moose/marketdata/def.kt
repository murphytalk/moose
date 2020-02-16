package moose.marketdata

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.JsonObject

data class Ticker(val name: String = "")
data class MarketDataPayload(val price:Int = -1, val receivedTime: Long = 0)
data class MarketData(val ticker: Ticker = Ticker(), val payload:MarketDataPayload = MarketDataPayload(), var publishTime:Long = 0)

class MarketDataCodec : MessageCodec<MarketData, MarketData>{
    override fun encodeToWire(buffer: Buffer?, s: MarketData?) {
        val me = JsonObject.mapFrom(s)
        me.writeToBuffer(buffer)
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer?): MarketData {
        buffer?.let {
            val obj = JsonObject()
            obj.readFromBuffer(pos, buffer)
            return obj.mapTo(MarketData::class.java)
        }
        return MarketData()
   }

    override fun systemCodecID(): Byte {
        return -1
    }


    override fun transform(s: MarketData?): MarketData? {
        return s
    }

    override fun name(): String {
        return this.javaClass.simpleName
    }

}

object ListCodec{
    fun <T>encode(buffer: Buffer, l: List<T>?){
        l?.let{
            buffer.appendInt(l.size)
            for(x in l){
                // Easiest ways is using JSON object
                val json = JsonObject.mapFrom(x)
                json.writeToBuffer(buffer)
            }
        }
    }

    inline fun <reified T>decode(pos: Int, buffer:Buffer): List<T>{
        val lst = mutableListOf<T>()
        val len = buffer.getInt(pos)
        var p = pos + Int.SIZE_BYTES
        for (i in 0 until len){
            val json = JsonObject()
            p = json.readFromBuffer(p, buffer)
            lst.add(json.mapTo(T::class.java))
        }
        return lst
    }
}

const val tickerListCodec = "tickerListCodec"
class TickerListCodec : MessageCodec<List<Ticker>, List<Ticker>>{
    override fun systemCodecID(): Byte {
        return -1
    }


    override fun transform(s: List<Ticker>?): List<Ticker>? {
        return s
    }

    override fun name(): String {
        return tickerListCodec
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer?): List<Ticker> {
        buffer?.let {
            return ListCodec.decode<Ticker>(pos, it)
        }
        return listOf()
    }

    override fun encodeToWire(buffer: Buffer?, s: List<Ticker>?) {
        buffer?.let {
            ListCodec.encode(buffer, s)
        }
    }
}

interface EndPoint{
    fun onPriceUpdated(ticker: Ticker, price: Int)
}
