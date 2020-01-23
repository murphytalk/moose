package moose

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.*
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
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
    marketdata_status
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
                JsonObject.mapFrom(msg), // to encode data class directly need to write custom message codec
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
        val configFuture = ConfigRetriever.getConfigAsFuture(setupConfig())

        // chaining future of config load => deploy MD publisher => deploy http => final handle
        // Note compose() is only get called when future is successful and the parameter is retrieved value

        configFuture.compose { config ->
            logger.info("Config is loaded {}", config.encodePrettily())
            val publisherDeployment = Promise.promise<String>()
            vertx.deployVerticle(MarketDataPublisher(), DeploymentOptions().setConfig(config), publisherDeployment)
            publisherDeployment.future()
        }.compose{ _ ->  // deploy id, not used
            val httpDeployment = Promise.promise<String>()
            val httpConfig = configFuture.result().getJsonObject("http")
            vertx.deployVerticle(
                    "moose.http.HttpServerVerticle",
                    DeploymentOptions().setInstances(httpConfig.getInteger("number")).setConfig(httpConfig),
                    httpDeployment)
            httpDeployment.future()
        }.setHandler { ar ->
            if (ar.succeeded()) {
                val genConfig = configFuture.result().getJsonObject("generator")
                Generator.start(
                        genConfig.getInteger("tickers"),
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
