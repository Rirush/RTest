package rirush.rtest.server

import io.vertx.rxjava.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.rxjava.ext.asyncsql.PostgreSQLClient
import mu.KotlinLogging

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    val httpServer = vertx.createHttpServer()
    val router = Router.router(vertx.delegate)

    val databaseConfig = json {
        obj("username" to "rtest")
        obj("password" to "dbpass")
        obj("database" to "rtest")
    }
    val databaseClient = PostgreSQLClient.createShared(vertx, databaseConfig)

    val server = Server(router, databaseClient)

    val logger = KotlinLogging.logger {}

    logger.info { "Starting server on port 8080" }

    httpServer.requestHandler { router.accept(it.delegate) }.listen(8080)
}