package moose.data

import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import moose.Address
import moose.marketdata.Generator
import moose.marketdata.Ticker

class DataService : AbstractVerticle() {
    var tickers : List<Ticker>? = null

    override fun start(){
        // in the real world the list could be loaded from an external storage e.g. DB
        tickers = Generator.genTickers(config().getInteger("tickers"))

        vertx.eventBus().consumer<String>(Address.data_service.name){ m ->
            // just to avoid to write a List codec ...
            val payload = JsonArray()
            tickers?.asSequence()?.fold(payload){  accu, ticker ->
               accu.add(JsonObject.mapFrom(ticker))
            }
            m.reply(payload)
        }
    }
}
