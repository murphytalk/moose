package moose.http

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.Address
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
        // market data init paint
        api.get("/md/initpaint").handler{ctx -> this.apiInitPaint(ctx)}
        router.mountSubRouter("/api", api)

        server.requestHandler(router).listen(8000) { ar ->
            if (ar.succeeded()){
                logger.info("http")
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
        vertx.eventBus().request<JsonObject>(Address.marketdata_publisher.name, JsonObject(), options) {reply ->
            val restResp = routingContext.response()
            restResp.putHeader("Content-Type", "application/json")
            if (reply.succeeded()){
                restResp.statusCode = 200
                val snapshot  = reply.result().body()
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