package rirush.rtest.server

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import mu.KotlinLogging

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    router.route().handler { ctx ->
        val response = ctx.response()
        response.putHeader("Content-Type", "text/plain")
                .end("SERVER STUB; NOT IMPLEMENTED")
    }

    var logger = KotlinLogging.logger {}
    logger.info { "Starting server on port 8080" }

    server.requestHandler { router.accept(it) }.listen(8080)
}