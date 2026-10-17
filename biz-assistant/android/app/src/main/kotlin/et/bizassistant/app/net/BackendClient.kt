package et.bizassistant.app.net

import android.content.Context
import et.bizassistant.core.AiClient
import et.bizassistant.core.AiRequest
import et.bizassistant.core.AiResult
import et.bizassistant.core.PlanId
import et.bizassistant.core.UsageQuota
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONObject

/**
 * Talks to our own backend only. The app holds an anonymous device token issued by the backend;
 * it never holds an AI provider key. Calls are blocking – invoke them from a background dispatcher.
 */
class BackendClient(context: Context, private val baseUrl: String) : AiClient {

    private val prefs = context.getSharedPreferences("backend", Context.MODE_PRIVATE)

    data class Response(val code: Int, val json: JSONObject)

    private fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, consent: Boolean = false): Response {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Accept", "application/json")
            if (auth) conn.setRequestProperty("Authorization", "Bearer ${deviceToken()}")
            if (consent) conn.setRequestProperty("X-AI-Consent", "granted")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(code, if (text.isBlank()) JSONObject() else JSONObject(text))
        } finally {
            conn.disconnect()
        }
    }

    @Synchronized
    private fun deviceToken(): String {
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val r = call("POST", "/v1/devices", JSONObject(), auth = false)
        if (r.code != 201) throw IOException("Could not register device (${r.code})")
        val token = r.json.getString("token")
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    private fun withAuthRetry(block: () -> Response): Response {
        val first = block()
        if (first.code != 401) return first
        prefs.edit().remove(KEY_TOKEN).apply() // token was revoked (e.g. data deleted); register again
        return block()
    }

    override fun generate(request: AiRequest): AiResult {
        val body = JSONObject()
            .put("consent", true)
            .put("task", request.task.apiName)
            .put("language", request.language.tag)
            .put("businessName", request.businessName)
            .put("inputs", JSONObject(request.inputs))
            .put("facts", JSONObject(request.facts))
        val r = try {
            withAuthRetry { call("POST", "/v1/ai/generate", body, consent = true) }
        } catch (e: IOException) {
            return AiResult.Failure(AiResult.Failure.Kind.UNAVAILABLE, e.message.orEmpty())
        }
        val quota = r.json.optJSONObject("usage")?.let(::parseUsage)
        return when (r.code) {
            200 -> AiResult.Success(r.json.optString("text"), quota)
            403 -> AiResult.Failure(AiResult.Failure.Kind.CONSENT_REQUIRED, quota = quota)
            429 -> AiResult.Failure(AiResult.Failure.Kind.QUOTA_EXCEEDED, quota = quota)
            422 -> AiResult.Failure(AiResult.Failure.Kind.REJECTED, quota = quota)
            else -> AiResult.Failure(AiResult.Failure.Kind.UNAVAILABLE, r.json.optString("error"), quota)
        }
    }

    fun fetchUsage(): UsageQuota? = try {
        val r = withAuthRetry { call("GET", "/v1/usage") }
        if (r.code == 200) parseUsage(r.json) else null
    } catch (e: IOException) {
        null
    }

    /** Returns true only if the backend confirms a checkout; payments are not configured by default. */
    fun startCheckout(planOrBundle: String): Boolean = try {
        val r = withAuthRetry { call("POST", "/v1/subscriptions/checkout", JSONObject().put("plan", planOrBundle)) }
        r.code == 200
    } catch (e: IOException) {
        false
    }

    /** Deletes the server-side counters for this device and forgets the token. */
    fun deleteServerData() {
        try {
            if (prefs.contains(KEY_TOKEN)) call("DELETE", "/v1/devices/me")
        } catch (_: IOException) {
            // Offline: the token is dropped locally anyway, so nothing links this device to it.
        }
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private fun parseUsage(o: JSONObject) = UsageQuota(
        plan = runCatching { PlanId.valueOf(o.optString("plan", "free").uppercase()) }.getOrDefault(PlanId.FREE),
        used = o.optInt("used"),
        limit = o.optInt("limit"),
        bonusRemaining = o.optInt("bonusRemaining"),
        resetsOn = runCatching { LocalDate.parse(o.optString("resetsOn")) }.getOrDefault(LocalDate.now().withDayOfMonth(1).plusMonths(1)),
    )

    private companion object {
        const val KEY_TOKEN = "device_token"
    }
}
