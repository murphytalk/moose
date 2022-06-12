package moose.http

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.ext.auth.htpasswd.HtpasswdAuth
import io.vertx.ext.auth.htpasswd.HtpasswdAuthOptions
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BasicAuthHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import moose.AddressMarketDataPublisher
import moose.AddressMarketDataStatus
import moose.MarketDataActionAction
import moose.MarketDataActionInitPaint
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate

class HttpServerVerticle() : AbstractVerticle() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpServerVerticle::class.java)
        const val adminUser = "admin"
    }

    private var template: PebbleTemplateEngine? = null

    private fun createAuthProvider(): HtpasswdAuth {
        val htpasswd = System.getProperty("htpasswd")
        if (htpasswd == null || htpasswd.isEmpty()) {
            throw RuntimeException("htpasswd property not defined!")
        }
        if (!File(htpasswd).isFile) {
            throw RuntimeException("$htpasswd is not a file!")
        }
        return HtpasswdAuth.create(vertx, HtpasswdAuthOptions().setHtpasswdFile(htpasswd))
    }

    private fun authorize(authorizedUser: String, ctx: RoutingContext, handler: (RoutingContext) -> Unit) {
        if (ctx.user() is HtpasswdUser) {
            val user = ctx.user().principal().getString("username")
            if (user == authorizedUser) {
                handler(ctx)
                return
            }
        }
        ctx.response().statusCode = HttpResponseStatus.UNAUTHORIZED.code()
    }

    override fun start(promise: Promise<Void>) {
        val router = Router.router(vertx)

        try {
            //  password protected admin page
            val authProvider = createAuthProvider()
            val basicAuth = BasicAuthHandler.create(authProvider, "moose")
            router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setAuthProvider(authProvider))
            router.route("/admin/*").handler(basicAuth)
            val admin = Router.router(vertx)
            admin.get("/redis/connect").handler { ctx ->
                authorize(adminUser, ctx) {
                    it.response().putHeader("Content-Type", "text/html").end("Force redis connection")
                }
            }
        } catch (e: Exception) {
            logger.error("No admin page !!! {}", e.message)
        }

        //  index page
        router.get("/").handler { ctx -> this.indexPage(ctx) }
        //  static contents
        router.route("/static/*").handler(StaticHandler.create())

        router.get("/test").handler { ctx -> this.indexPage(ctx) }

        //  API
        val api = Router.router(vertx)
        ///  market data init paint
        api.get("/md/initpaint").handler { ctx -> this.apiInitPaint(ctx) }
        router.mountSubRouter("/api", api)
        //  to web frontend
        val sockJSHandler = SockJSHandler.create(vertx);
        val bridgeOptions = BridgeOptions()
                .addOutboundPermitted(PermittedOptions().setAddress(AddressMarketDataStatus))
        sockJSHandler.bridge(bridgeOptions)
        router.route("/eventbus/*").handler(sockJSHandler)

        val server = vertx.createHttpServer()
        server.requestHandler(router).listen(config().getInteger("port")) { ar ->
            if (ar.succeeded()) {
                template = PebbleTemplateEngine.create(vertx)
                logger.info("http started at port {}", config().getInteger("port"))
                promise.complete()
            } else {
                logger.error("Could not start HTTP server", ar.cause())
                promise.fail(ar.cause())
            }
        }
    }

    private fun indexPage(routingContext: RoutingContext) {
        routingContext.put("year", LocalDate.now().year)
        template?.render(routingContext.data(), "templates/index") { ar ->
            if (ar.succeeded()) {
                routingContext.response().putHeader("Content-Type", "text/html")
                routingContext.response().end(ar.result())
            } else {
                routingContext.fail(ar.cause())
            }
        }
    }

    private fun apiInitPaint(routingContext: RoutingContext) {
        val options = DeliveryOptions().addHeader(MarketDataActionAction, MarketDataActionInitPaint)
        vertx.eventBus().request<JsonArray>(AddressMarketDataPublisher, null, options) { reply ->
            val restResp = routingContext.response()
            restResp.putHeader("Content-Type", "application/json")
            if (reply.succeeded()) {
                restResp.statusCode = 200
                val snapshot = reply.result().body()
                restResp.end(snapshot.encode())
            } else {
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