package moose.marketdata

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.JsonObject

data class Ticker(val name: String)
data class MarketDataPayload(val price:Int, val receivedTime: Long)
data class MarketData(val ticker: Ticker, val payload:MarketDataPayload, var publishTime:Long = 0)

class MarketDataCodec : MessageCodec<MarketData, MarketData>{
    override fun encodeToWire(buffer: Buffer?, s: MarketData?) {
        val me = JsonObject.mapFrom(s)
        val s = me.encode()
        val encoded: Buffer = me.toBuffer()
        buffer!!.appendInt(encoded.length())
        buffer.appendBuffer(encoded)
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer?): MarketData {
        val length = buffer!!.getInt(pos)
        val pos2 = pos + 4
        val obj = JsonObject(buffer.slice(pos2, pos2 + length))
        return obj.mapTo(MarketData::class.java)
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

interface EndPoint{
    fun onPriceUpdated(ticker: Ticker, price: Int)
}
