package et.ethiolive.app.data

import et.ethiolive.app.BuildConfig
import et.ethiolive.core.AppException
import et.ethiolive.core.ServerError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException

/** Whether this build was given a backend. Without it the app shows a setup notice instead of pretending to work. */
object BackendConfig {
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("http") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    val streamingConfigured: Boolean get() = BuildConfig.AGORA_APP_ID.isNotBlank()
    val googleSignInConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val paymentProviders: Set<String> get() = BuildConfig.PAYMENT_PROVIDERS.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val pushConfigured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()
}

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Thin wrapper around the Supabase client. Every write goes through a server RPC or edge function; the client
 * never computes balances, scores or viewer counts. All failures surface as [AppException] with a parsed code.
 */
class Backend {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.invalid" },
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "not-configured" },
    ) {
        defaultSerializer = KotlinXSerializer(AppJson)
        install(Auth) {
            scheme = "ethiolive"
            host = "auth"
        }
        install(Postgrest)
        install(Realtime)
        install(Functions)
        install(Storage)
    }

    /** Process-wide scope for fire-and-forget cleanup (leaving rooms, removing channels) that must outlive a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userId: String? get() = client.auth.currentUserOrNull()?.id

    fun requireUserId(): String = userId ?: throw AppException(ServerError.NOT_AUTHENTICATED)

    suspend fun rpcRaw(function: String, params: JsonObject = JsonObject(emptyMap())): String = call {
        client.postgrest.rpc(function, params).data
    }

    suspend inline fun <reified T> rpc(function: String, params: JsonObject = JsonObject(emptyMap())): T {
        val raw = rpcRaw(function, params)
        return decode(raw)
    }

    suspend fun rpcUnit(function: String, params: JsonObject = JsonObject(emptyMap())) {
        rpcRaw(function, params)
    }

    suspend inline fun <reified T> function(name: String, body: JsonObject): T {
        val raw = call { client.functions.invoke(name, body).bodyAsText() }
        return decode(raw)
    }

    inline fun <reified T> decode(raw: String): T = try {
        AppJson.decodeFromString<T>(raw)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw AppException(ServerError.UNKNOWN, e)
    }

    inline fun <reified T> decode(element: JsonElement): T = try {
        AppJson.decodeFromJsonElement<T>(element)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw AppException(ServerError.UNKNOWN, e)
    }

    suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        throw e
    } catch (e: RestException) {
        throw AppException(ServerError.parse(listOfNotNull(e.error, e.description, e.message).joinToString(" ")), e)
    } catch (e: HttpRequestException) {
        throw AppException(ServerError.NETWORK, e)
    } catch (e: IOException) {
        throw AppException(ServerError.NETWORK, e)
    } catch (e: Exception) {
        val parsed = ServerError.parse(e.message)
        throw AppException(if (parsed == ServerError.UNKNOWN && e.javaClass.name.contains("Timeout")) ServerError.NETWORK else parsed, e)
    }
}

/** Builds RPC parameter objects; nulls become JSON null so optional SQL arguments receive NULL. */
fun params(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((k, v) in pairs) put(k, v.toJson())
}

fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is List<*> -> kotlinx.serialization.json.JsonArray(map { it.toJson() })
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJson() })
    else -> JsonPrimitive(toString())
}
