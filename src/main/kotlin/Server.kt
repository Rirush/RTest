package rirush.rtest.server

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

class Server(router: Router) {
    companion object Logging {
        val logger = KotlinLogging.logger {}
    }

    init {
        logger.info { "Registering routes" }
        router.route().handler(::stub)
    }

    private fun stub(ctx: RoutingContext) {
        logger.info { "Request from ${ctx.request().remoteAddress()}" }
        val response = ctx.response()
        response.putHeader("Content-Type", "text/plain")
                .end("SERVER STUB; NOT IMPLEMENTED")
    }
}