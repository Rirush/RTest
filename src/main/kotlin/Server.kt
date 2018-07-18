package rirush.rtest.server

import com.google.gson.GsonBuilder
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import mu.KotlinLogging
import java.util.*

data class Result(val success: Boolean, val uuid: String? = null, val user: User? = null, val reason: String? = null)

// Server implementation roadmap
// POST /connect - Register session and obtain its UUID [ ]
//  Doesn't require authorization to be used
//  Arguments:
//   username: String - Account username
//   password: String - Account password
//  Returns:
//   success: Boolean - Shows whether authorization was successful or not
//   uuid: String - If `success` is true, contains session UUID used for further actions

// POST /disconnect/:uuid - Revoke session [ ]
//  Requires authorization to be used
//  Returns `success` = false if session UUID is invalid
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   None
//  Returns:
//   success: Boolean - Shows whether revoke was successful or not

// GET /me/:uuid - Get current user information [ ]
//  Requires authorization to be used
//  Doesn't return anything if session UUID is invalid
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   None
//  Returns:
//   TODO: Describe `User` structure
//   user: User - Current user

// POST /me/:uuid - Update current user information [ ]
//  Requires authorization to be used
//  Returns `success` = false and empty `reason` if session UUID is invalid
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   user: User - Updated user. Empty fields remain untouched
//  Returns:
//   success: Boolean - Shows whether update was successful or not
//   reason: String - If `success` if false, contains error message
class Server(router: Router) {
    // Object used for storing one logger for all instances of `Server`
    companion object Logging {
        val logger = KotlinLogging.logger {}
        val gsonBuilder = GsonBuilder()
    }

    init {
        logger.info { "Registering routes" }
        // Enable chunked response
        router.route().handler {
            it.response().isChunked = true
            it.next()
        }
        // Logging "middleware"
        router.route().handler(::log)
        // Enable `BodyHandler` to read body
        router.route().handler(BodyHandler.create())
        // POST /connect method handler
        router.route("/connect/").method(HttpMethod.POST).handler(::connect)
        // POST /disconnect method handler
        router.route("/disconnect/:uuid/").method(HttpMethod.POST).handler(::disconnect)
        // Stub handler that answers with a string to all requests
        // 404 handler, that should be called if no handlers are associated with path
        router.route().handler(::notFound)
    }

    // Logging "middleware" that called before all handlers
    private fun log(ctx: RoutingContext) {
        logger.info { "Request from ${ctx.request().remoteAddress().host()} to ${ctx.request().remoteAddress().path()}" }
        ctx.next()
    }

    private fun connect(ctx: RoutingContext) {
        val response = ctx.response()
        val request = ctx.request()
        val attrs = request.formAttributes()
        val gson = gsonBuilder.create()

        val username = attrs["username"]
        val password = attrs["password"]
        if(username == null || password == null) {
            response.write(gson.toJson(Result(success = false, reason = "Missing `username` or `password`"))).end()
            return
        }
        username as String
        password as String

        // TODO: Actually check username and password
        // But for now let's pretend that all passwords are correct

        val user = User(username)
        val id = ServerState.createSession(user)

        response.write(gson.toJson(Result(success = true, uuid = id.id.toString()))).end()
    }

    private fun disconnect(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()

        val uuid = ctx.request().getParam("uuid")
        if(uuid == null) {
            response.write(gson.toJson(Result(success = false, reason = "Missing `uuid`"))).end()
            return
        }
        uuid as String
        val id: UUID
        try {
            id = UUID.fromString(uuid)
        } catch(e: Exception) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid session"))).end()
            return
        }

        try {
            ServerState.revokeSession(SessionIdentifier(id))
        } catch(e: NoSuchSessionException) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid session"))).end()
            return
        }

        response.write(gson.toJson(Result(success = true))).end()
    }

    // 404 handler that registered after all handlers and called only if there're no other handlers for requested path
    private fun notFound(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()
        response.putHeader("Content-Type", "text/plain")
                .setStatusCode(404)
                .end(gson.toJson(Result(success = false, reason = "No such method found")))
    }
}