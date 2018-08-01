package rirush.rtest.server

import java.util.*

class SessionExistsException(override var message: String) : Exception(message)
class NoSuchSessionException(override var message: String) : Exception(message)

class User(val id: UUID = UUID.randomUUID(), val username: String, var firstName: String? = null, var lastName: String? = null, var student: Boolean? = null) {
    // Compare users not by hash of object, but by its fields
    // Required by `findUsers` to function properly
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other?.javaClass != javaClass) return false
        other as User
        return other.username == username
    }

    override fun hashCode(): Int {
        return username.hashCode()
    }
}

class Session(val user: User) {
    // Compare sessions not by hash of object, but by user
    // Required by `findUsers` to function properly
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other?.javaClass != javaClass) return false
        other as Session
        if(other.user === user) return true
        return other.user == user
    }

    override fun hashCode(): Int {
        return user.hashCode()
    }
}

class SessionIdentifier constructor(val id: UUID) {
    companion object {
        // Create `SessionIdentifier` with randomly generated UUID
        fun newIdentifier(): SessionIdentifier {
            val uuid = UUID.randomUUID()
            return SessionIdentifier(uuid)
        }
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other?.javaClass != javaClass) return false
        other as SessionIdentifier
        return other.id.compareTo(id) == 0
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

object ServerState {
    // `HashMap` containing all user sessions
    private var sessions: HashMap<SessionIdentifier, Session> = HashMap()

    // Create session from `user` and save it in `sessions` HashMap under `id`
    // If session with specified `id` already exists, `SessionExistsException` will be thrown
    fun createSession(id: SessionIdentifier, user: User) {
        val session = Session(user)
        if(sessions.contains(id)) {
            throw SessionExistsException("Session with ID '${id.id}' already exists")
        }
        sessions[id] = session
    }

    // Create session from `user` and store it under generated `SessionIdentifier`, returns this identifier
    fun createSession(user: User): SessionIdentifier {
        val session = Session(user)
        val id = SessionIdentifier.newIdentifier()
        // Assuming that chances of UUID collision are EXTREMELY low, no collision checks performed
        sessions[id] = session
        return id
    }

    fun updateSession(id: SessionIdentifier, user: User) {
        val session = Session(user)
        if(!sessions.contains(id)) {
            throw NoSuchSessionException("Session with ID '${id.id}' doesn't exist")
        }
        sessions[id] = session
    }

    // Return `Session` from `sessions` `HashMap` if session with specified `SessionIdentifier` exists, or null
    fun findSession(id: SessionIdentifier): Session? {
        return sessions[id]
    }

    // Return all sessions' UUID of specified `user`
    fun findSessions(user: User): Array<SessionIdentifier> {
        val filtered = sessions.filterValues { it.user == user }
        return filtered.keys.toTypedArray()
    }

    // Delete session with specified `id` from `sessions` map
    // If session doesn't exist, `NoSuchSessionException` will be thrown
    fun revokeSession(id: SessionIdentifier) {
        if(!sessions.containsKey(id)) {
            throw NoSuchSessionException("Session with ID '${id.id}' doesn't exist")
        }
        sessions.remove(id)
    }

    // Check whether session with specified `id` exists or not
    fun sessionExists(id: SessionIdentifier): Boolean {
        return sessions.containsKey(id)
    }

    // Return session with specified `id` if it exists, or null
    fun getSession(id: SessionIdentifier): Session? {
        return sessions[id]
    }
}