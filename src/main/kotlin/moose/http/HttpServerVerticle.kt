package moose.http

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.Address
import moose.MainVerticle
import moose.MarketDataAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class HttpServerVerticle() : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpServerVerticle::class.java)
    }

    private var template : PebbleTemplateEngine? = null

    override fun start(promise: Promise<Void>) {
        template = PebbleTemplateEngine.create(vertx)
        val server = vertx.createHttpServer()
        val router =  Router.router(vertx)

        /// index page
        router.get("/").handler { ctx -> this.indexPage(ctx)}
        /// static contents
        router.route("/static/*").handler(StaticHandler.create())
        /// API
        val api = Router.router(vertx)
        //  market data init paint
        api.get("/md/initpaint").handler{ctx -> this.apiInitPaint(ctx)}
        router.mountSubRouter("/api", api)

        val sockJSHandler = SockJSHandler.create(vertx);
        val bridgeOptions = BridgeOptions()
                .addOutboundPermitted(PermittedOptions().setAddress(Address.marketdata_status.name))
        sockJSHandler.bridge(bridgeOptions)
        router.route("/eventbus/*").handler(sockJSHandler)

        server.requestHandler(router).listen(config().getInteger("port")) { ar ->
            if (ar.succeeded()){
                logger.info("http started at port {}", config().getInteger("port"))
                promise.complete()
            }
            else{
                logger.error("Could not start HTTP server", ar.cause())
                promise.fail(ar.cause())
            }
        }
    }

    private fun indexPage(routingContext: RoutingContext){
        routingContext.put("year", LocalDate.now().year)
        template?.render(routingContext.data(), "templates/index") { ar ->
            if (ar.succeeded()){
                routingContext.response().putHeader("Content-Type", "text/html")
                routingContext.response().end(ar.result())
            }
            else{
                routingContext.fail(ar.cause())
            }
        }
    }

    private fun apiInitPaint(routingContext: RoutingContext){
        val options = DeliveryOptions().addHeader(MarketDataAction.action.name, MarketDataAction.init_paint.name)
        vertx.eventBus().request<JsonArray>(Address.marketdata_publisher.name, null, options) { reply ->
            val restResp = routingContext.response()
            restResp.putHeader("Content-Type", "application/json")
            if (reply.succeeded()){
                restResp.statusCode = 200
                val snapshot = reply.result().body()
                restResp.end(snapshot.encode())
            }
            else{
                val resp = json {
                    obj(
                            "success" to false,
                            "error" to reply.cause().message
                    )
                }
                restResp.statusCode = 500
                restResp.end(resp.encode())
            }
        }
    }
}