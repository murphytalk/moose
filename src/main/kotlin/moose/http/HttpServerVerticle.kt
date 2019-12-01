package moose.http

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.ext.web.Router
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine
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
        router.get("/").handler { routingContext ->
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
}