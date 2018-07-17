package rirush.rtest.server

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import mu.KotlinLogging

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    val httpServer = vertx.createHttpServer()
    val router = Router.router(vertx)

    val server = Server(router)

    val logger = KotlinLogging.logger {}
    logger.info { "Starting server on port 8080" }

    httpServer.requestHandler { router.accept(it) }.listen(8080)
}