package moose

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.*
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.data.DataService
import moose.marketdata.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Address {
    marketdata_publisher,
    marketdata_status,
    data_service
}

enum class MarketDataAction {
    action,
    tick,
    init_paint
}

enum class ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION
}

object Timestamp {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    fun formatEpoch(epochMillis: Long, zone:ZoneId = ZoneId.systemDefault()): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(formatter)
}


class MainVerticle : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    }

    private inner class MarketDataEndpoint : EndPoint {
        override fun onPriceUpdated(ticker: Ticker, price: Int) {
            val msg = MarketData(ticker, MarketDataPayload(price, System.currentTimeMillis()))
            vertx.eventBus().send(
                Address.marketdata_publisher.name,
                msg,
                DeliveryOptions().addHeader(MarketDataAction.action.name, MarketDataAction.tick.name)
            )
        }
    }

    private fun setupConfig() : ConfigRetriever{
        var storeOptions = mutableListOf(
            configStoreOptionsOf(type="file", format = "yaml", config=json{obj("path" to "config.yaml")})
        )
        val externalConf = "conf/config.yaml"
        if (File(externalConf).exists()){
            storeOptions.add(configStoreOptionsOf(type="file", format = "yaml", config=json{obj("path" to externalConf)}))
        }

        return ConfigRetriever.create(vertx, ConfigRetrieverOptions().setStores(storeOptions))
    }

    override fun start(promise: Promise<Void>) {
        DatabindCodec.mapper().registerModule(KotlinModule())

        val configFuture = Future.future<JsonObject>{
            setupConfig().getConfig(it) //Promise extends Handler<AsyncResult<T>>
        }

        // chaining future:
        // 
        //     config load =>
        //     in parallel { deploy MD publisher / http / data service } =>
        //     request ticker list from data service =>
        //     final handle to start the randome market data generator
        //
        // Note compose() is only get called when future is successful and the parameter is retrieved value
        configFuture.compose { config ->
            logger.info("Config is loaded {}", config.encodePrettily())
            CompositeFuture.all(listOf(
                Future.future<String>{ publisher ->
                    vertx.deployVerticle(MarketDataPublisher(), DeploymentOptions().setConfig(config), publisher)
                },
                Future.future<String>{ http ->
                    val httpConfig = configFuture.result().getJsonObject("http")
                    vertx.deployVerticle(
                            "moose.http.HttpServerVerticle",
                            DeploymentOptions().setInstances(httpConfig.getInteger("number")).setConfig(httpConfig),
                            http)
                },
                Future.future<String>{ data ->
                    vertx.deployVerticle(DataService(), DeploymentOptions().setConfig(configFuture.result().getJsonObject("data")),data)
                }
            ))
       }.compose{ _ ->
           Future.future<List<Ticker>>{ tickersPromise ->
               vertx.eventBus().request<JsonArray>(Address.data_service.name, null) {
                   val objs = it.result().body().list as List<JsonObject>
                   val tickers = objs.map{ e -> e.mapTo(Ticker::class.java)}
                   tickersPromise.complete(tickers)
               }
           }
       }.setHandler { ar ->
           if(ar.succeeded()) {
               val tickers = ar.result()
               val genConfig = configFuture.result().getJsonObject("generator")
               Generator.start(
                       tickers,
                       genConfig.getInteger("min_price"),
                       genConfig.getInteger("max_price"),
                       genConfig.getInteger("min_interval"),
                       genConfig.getInteger("max_interval"),
                       MarketDataEndpoint())
               promise.complete()
           } else {
               promise.fail(ar.cause())
           }
       }
    }

    override fun stop() {
        logger.info("Stopping market data thread")
        Generator.stop()
        logger.info("Market data thread stopped")
    }
}
