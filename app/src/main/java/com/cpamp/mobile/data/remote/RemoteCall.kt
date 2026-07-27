package com.cpamp.mobile.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed class RemoteFailure(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class Unauthorized : RemoteFailure("REMOTE_UNAUTHORIZED")
    class RateLimited : RemoteFailure("REMOTE_RATE_LIMITED")
    class NotFound : RemoteFailure("REMOTE_NOT_FOUND")
    class Server(val statusCode: Int) : RemoteFailure("REMOTE_SERVER_ERROR")
    class Timeout(cause: Throwable) : RemoteFailure("REMOTE_TIMEOUT", cause)
    class Tls(cause: Throwable) : RemoteFailure("REMOTE_TLS_ERROR", cause)
    class Network(cause: Throwable) : RemoteFailure("REMOTE_NETWORK_ERROR", cause)
    class InvalidResponse(cause: Throwable) : RemoteFailure("REMOTE_INVALID_RESPONSE", cause)
}

suspend inline fun <T> remoteCall(crossinline block: suspend () -> T): T = try {
    block()
} catch (error: HttpException) {
    throw when (error.code()) {
        401, 403 -> RemoteFailure.Unauthorized()
        404 -> RemoteFailure.NotFound()
        429 -> RemoteFailure.RateLimited()
        else -> RemoteFailure.Server(error.code())
    }
} catch (error: SSLException) {
    throw RemoteFailure.Tls(error)
} catch (error: SocketTimeoutException) {
    throw RemoteFailure.Timeout(error)
} catch (error: SerializationException) {
    throw RemoteFailure.InvalidResponse(error)
} catch (error: IOException) {
    throw RemoteFailure.Network(error)
}

