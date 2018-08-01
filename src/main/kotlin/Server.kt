package rirush.rtest.server

import com.google.gson.GsonBuilder
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.rxjava.ext.asyncsql.AsyncSQLClient
import mu.KotlinLogging
import org.mindrot.jbcrypt.BCrypt
import java.util.*
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

data class Result(val success: Boolean, val uuid: String? = null, val user: User? = null, val reason: String? = null)

// Server implementation roadmap
// POST /connect - Register session and obtain its UUID [x]
//  Doesn't require authorization to be used
//  Arguments:
//   username: String - Account username
//   password: String - Account password
//  Returns:
//   success: Boolean - Shows whether authorization was successful or not
//   uuid: String - If `success` is true, contains session UUID used for further actions

// POST /disconnect/:uuid - Revoke session [x]
//  Requires authorization to be used
//  Returns `success` = false if session UUID is invalid
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   None
//  Returns:
//   success: Boolean - Shows whether revoke was successful or not

// User type structure:
//  id: String - User's UUID
//  username: String - User's username
//  firstName: String - User's first name
//  lastName: String - User's last name
//  student: Boolean - Shows whether user is a teacher or a student

// GET /me/:uuid - Get current user information [x]
//  Requires authorization to be used
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   None
//  Returns:
//   success: Boolean - Shows whether request was successful or not
//   reason: String - If request was unsuccessful, shows why
//   user: User - Current user

// POST /me/:uuid - Update current user information [x]
//  Requires authorization to be used
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   body - User - Updated user. Omitted fields remain untouched. `id` and `student` fields are always ignored
//  Returns:
//   success: Boolean - Shows whether update was successful or not
//   reason: String - If `success` if false, contains error message

// GET /users/:uuid - Get all users that match filters [ ]
//  Requires authorization to be used
//  Requires user to be teacher (`student` = false)
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   onlyStudents: Boolean - Return only students
//   grade: String - Filter students by grade.
//    "11" will return all students from eleventh grade; "11A" will return all students from "11A" class
//   onlyTeachers: Boolean - Return only teachers
// TODO: Add permission table and filter teachers by permissions on specific item (e.g. test or asset)
//  Returns:
//   success: Boolean - Shows whether request was successful or not
//   reason: String - If request was unsuccessful, shows why
//   users: Array<User> - Users that match filters

// POST /user/:uuid - Create or update user [ ]
//  Requires authorization to be used
//  Requires user to be teacher (`student` = false)
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   id: String - UUID of existing user to update. Omit this field to create new user
//   user: User - User information. All fields should be present if new user is being created
//    When updating, omitted fields remain unchanged
//  Returns:
//   success: Boolean - Shows whether update/creation was successful or not
//   reason: String - If update/creation was unsuccessful, shows why
//   uuid: String - When creating a new user, contains UUID of created user

// GET /user/:uuid - Get user [ ]
//  Requires authorization to be used
//  Requires user to be teacher (`student` = false)
//  Path arguments:
//   uuid: String - Session UUID
//  Arguments:
//   id: String - UUID of requested user
//  Returns:
//   success: Boolean - Shows whether request was successful or not
//   reason: String - If request was unsuccessful, shows why
//   user: User - Requested user
class Server(router: Router, private val database: AsyncSQLClient) {
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
            it.response().putHeader("Content-Type", "application/json")
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
        // GET /me method handler
        router.route("/me/:uuid/").method(HttpMethod.GET).handler(::getMe)
        // POST /me method handler
        router.route("/me/:uuid/").method(HttpMethod.POST).handler(::postMe)
        // Stub handler that answers with a string to all requests
        // 404 handler, that should be called if no handlers are associated with path
        router.route().handler(::notFound)
    }

    // Logging "middleware" that called before all handlers
    private fun log(ctx: RoutingContext) {
        val elapsedTime = measureTimeMillis { ctx.next() }
        logger.info { "${ctx.request().remoteAddress()} -> ${ctx.request().method()} ${ctx.request().path()} @ ${elapsedTime}ms" }
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

        // Query user's ID and password from database
        database.queryWithParams("SELECT ID, Password FROM Users WHERE Username = ? LIMIT 1", json {
            array(username)
        }) { result ->
            if(!result.succeeded()) {
                // Write error to log
                val cause = result.cause()
                logger.error { cause.localizedMessage }
                response.write(gson.toJson(Result(success = false, reason = "Internal server error"))).end()
                return@queryWithParams
            }

            val resultSet = result.result()
            if(resultSet.numRows == 0) {
                    response.write(gson.toJson(Result(success = false, reason = "No such user found"))).end()
                    return@queryWithParams
                }

            // There's no point in iterating through the rows since `Username` is unique and `LIMIT 1` is applied (just to be sure)
            val row = resultSet.rows[0]
            val userID = row.getString("id")
            val userPassword = row.getString("password")

            // Passwords in database should be hashed using bcrypt
            if(!BCrypt.checkpw(password, userPassword)) {
                    response.write(gson.toJson(Result(success = false, reason = "Incorrect password"))).end()
                    return@queryWithParams
                }

            // At this point, specified password is correct, so we store it in a session and return its ID to user
            val user = User(id = UUID.fromString(userID), username = username)
            val session = ServerState.createSession(user)

            response.write(gson.toJson(Result(success = true, uuid = session.id.toString()))).end()
        }
    }

    private fun disconnect(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()

        // Check if UUID is valid and cast it into `UUID` class
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

        // Revoke session
        try {
            ServerState.revokeSession(SessionIdentifier(id))
        } catch(e: NoSuchSessionException) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid session"))).end()
            return
        }

        response.write(gson.toJson(Result(success = true))).end()
    }

    private fun getMe(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()

        // Check if UUID is valid and cast it into `UUID` class
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

        // Get session
        val session = ServerState.findSession(SessionIdentifier(id))
        if(session == null) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid session"))).end()
            return
        }

        // Query all user information (Session.User may contain incomplete data)
        database.queryWithParams("SELECT Username, FirstName, LastName, Student FROM Users WHERE ID = ? LIMIT 1", json {
            array(session.user.id.toString())
        }) { result ->
            if(!result.succeeded()) {
                val cause = result.cause()
                logger.error { cause.localizedMessage }
                response.write(gson.toJson(Result(success = false, reason = "Internal server error"))).end()
                return@queryWithParams
            }

            val resultSet = result.result()
            if(resultSet.numRows == 0) {
                    response.write(gson.toJson(Result(success = false, reason = "User bound to this session was removed, this session will be revoked"))).end()
                    ServerState.revokeSession(SessionIdentifier(id))
                    return@queryWithParams
                }

            val row = resultSet.rows[0]
            val username = row.getString("username")
            val firstName = row.getString("firstname")
            val lastName = row.getString("lastname")
            val student = row.getBoolean("student")

            val user = User(id = session.user.id, firstName = firstName, lastName = lastName, username = username, student = student)

            response.write(gson.toJson(Result(success = true, user = user))).end()
        }
    }

    private fun postMe(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()

        // Check if UUID is valid and cast it into `UUID` class
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

        // Get session
        val session = ServerState.findSession(SessionIdentifier(id))
        if(session == null) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid session"))).end()
            return
        }

        val body: JsonObject
        try {
            body = ctx.bodyAsJson
        } catch(e: Exception) {
            response.write(gson.toJson(Result(success = false, reason = "Invalid body"))).end()
            return
        }

        // Query all user information (Session.User may contain incomplete data)
        database.queryWithParams("SELECT Username, FirstName, LastName, Student FROM Users WHERE ID = ? LIMIT 1", json {
            array(session.user.id.toString())
        }) { result ->
            if(!result.succeeded()) {
                val cause = result.cause()
                logger.error { cause.localizedMessage }
                response.write(gson.toJson(Result(success = false, reason = "Internal server error"))).end()
                return@queryWithParams
            }
            val resultSet = result.result()
            if(resultSet.numRows == 0) {
                response.write(gson.toJson(Result(success = false, reason = "User bound to this session was removed, this session will be revoked"))).end()
                ServerState.revokeSession(SessionIdentifier(id))
                return@queryWithParams
            }

            val row = resultSet.rows[0]
            val username = row.getString("username")
            val firstName = row.getString("firstname")
            val lastName = row.getString("lastname")
            val student = row.getBoolean("student")

            val user = User(id = session.user.id, firstName = body.getString("firstName", firstName),
                        lastName = body.getString("lastName", lastName), username = body.getString("username", username),
                        student = student)

            try {
                ServerState.updateSession(SessionIdentifier(id), user)
            } catch(e: Exception) {
                response.write(gson.toJson(Result(success = false, reason = "Session was revoked while this request was processed")))
                return@queryWithParams
            }

            database.updateWithParams("UPDATE Users SET FirstName = ?, LastName = ?, Username = ? WHERE ID = ?", json {
                    array(user.firstName, user.lastName, user.username, user.id)
            }) { res ->
                if(!res.succeeded()) {
                    val cause = result.cause()
                    logger.error { cause.localizedMessage }
                    response.write(gson.toJson(Result(success = false, reason = "Internal server error"))).end()
                    return@updateWithParams
                }
                response.write(gson.toJson(Result(success = true))).end()
            }
        }
    }

    // 404 handler that registered after all handlers and called only if there're no other handlers for requested path
    private fun notFound(ctx: RoutingContext) {
        val response = ctx.response()
        val gson = gsonBuilder.create()
        response.setStatusCode(404)
                .end(gson.toJson(Result(success = false, reason = "No such method found")))
    }
}