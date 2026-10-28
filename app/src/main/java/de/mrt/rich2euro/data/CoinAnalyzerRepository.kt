package de.mrt.rich2euro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import de.mrt.rich2euro.model.CoinAnalysisResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CoinAnalyzerRepository"
private const val MAX_IMAGE_DIMENSION_PX = 896
private const val JPEG_QUALITY = 78
private const val MODEL_DISCOVERY_TIMEOUT_MS = 5_500L
private const val FAST_SERVER_TIMEOUT_MS = 18_000L
private const val PRO_SERVER_TIMEOUT_MS = 25_000L
private const val TOTAL_FAILOVER_BUDGET_MS = 75_000L
private const val SERVER_COOLDOWN_MS = 90_000L
private const val MODEL_LIST_CACHE_TTL_MS = 10 * 60 * 1000L
private const val MAX_ENDPOINTS_TO_ATTEMPT = 6

private const val GEMINI_V1BETA_BASE = "https://generativelanguage.googleapis.com/v1beta"
private const val GEMINI_V1_BASE = "https://generativelanguage.googleapis.com/v1"
private const val GROQ_OPENAI_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

class InvalidApiKeyException(message: String) : Exception(message)

private enum class EndpointProtocol {
    GEMINI_V1BETA_SCHEMA,
    GEMINI_V1_JSON_PROMPT,
    OPENAI_COMPATIBLE_VISION
}

private data class AnalysisServerEndpoint(
    val id: String,
    val displayLabel: String,
    val url: String,
    val modelId: String,
    val apiKey: String,
    val protocol: EndpointProtocol,
    val timeoutMs: Long
)

private data class CachedModelList(
    val fetchedAtMs: Long,
    val rankedModels: List<String>
)

class CoinAnalyzerRepository {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(26, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Tracks temporarily overloaded/slow servers so subsequent requests skip them immediately
    private val serverCooldownUntilMs = ConcurrentHashMap<String, Long>()

    // Remembers the fastest working endpoint ID to try it first on subsequent scans
    @Volatile
    private var stickyHealthyEndpointId: String? = null

    private val cachedGeminiModelsByKeyHash = ConcurrentHashMap<Int, CachedModelList>()

    suspend fun analyzeCoinImages(
        context: Context,
        primaryApiKey: String,
        backupApiKey: String? = null,
        obverseUri: Uri,
        reverseUri: Uri,
        onStatusUpdate: (String) -> Unit = {}
    ): CoinAnalysisResult {
        val keys = listOfNotNull(
            primaryApiKey.trim().takeIf { it.isNotEmpty() },
            backupApiKey?.trim()?.takeIf { it.isNotEmpty() && it != primaryApiKey.trim() }
        )

        if (keys.isEmpty()) {
            throw InvalidApiKeyException(
                "Kein API-Key hinterlegt. Bitte gib zuerst deinen eigenen API-Key ein."
            )
        }

        onStatusUpdate("Münzbilder werden für schnelle Übertragung optimiert…")
        val (obverseBase64, reverseBase64) = withContext(Dispatchers.IO) {
            val firstBitmap = decodeScaledBitmap(context, obverseUri)
            val secondBitmap = decodeScaledBitmap(context, reverseUri)
            encodeBitmapToJpegBase64(firstBitmap) to encodeBitmapToJpegBase64(secondBitmap)
        }

        val structuredJsonString = withTimeout(TOTAL_FAILOVER_BUDGET_MS) {
            executeMultiServerFailoverCascade(
                apiKeys = keys,
                obverseBase64 = obverseBase64,
                reverseBase64 = reverseBase64,
                onStatusUpdate = onStatusUpdate
            )
        }

        val cleanedJsonString = extractCleanJsonObject(structuredJsonString)
        val json = JSONObject(cleanedJsonString)

        return CoinAnalysisResult(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            isValidTwoEuroCoin = json.optBoolean("isValidTwoEuroCoin", true),
            coinTitle = json.optString("coinTitle", "2-Euro-Münze").ifBlank { "2-Euro-Münze" },
            countryName = json.optString("countryName", "Unbekannt").ifBlank { "Unbekannt" },
            mintMark = json.optString("mintMark", "Nicht eindeutig erkennbar")
                .ifBlank { "Nicht eindeutig erkennbar" },
            coinCategory = json.optString("coinCategory", "2-Euro-Münze")
                .ifBlank { "2-Euro-Münze" },
            rarityLevel = json.optString("rarityLevel", "Siehe Gutachten")
                .ifBlank { "Siehe Gutachten" },
            conditionGrade = json.optString("conditionGrade", "Umlauferhaltung")
                .ifBlank { "Umlauferhaltung" },
            estimatedValueRangeEur = json.optString("estimatedValueRangeEur", "2,00 €")
                .ifBlank { "2,00 €" },
            landUndMotiv = json.optString("landUndMotiv", "Keine Angabe verfügbar."),
            praegejahr = json.optString("praegejahr", "Nicht eindeutig ablesbar."),
            auflageUndSeltenheit = json.optString("auflageUndSeltenheit", "Keine Angabe verfügbar."),
            zustandUndErhaltung = json.optString("zustandUndErhaltung", "Keine Angabe verfügbar."),
            realistischerMarktwert = json.optString("realistischerMarktwert", "Keine Angabe verfügbar."),
            hinweisFakePreise = json.optString("hinweisFakePreise", "Keine Angabe verfügbar."),
            obverseUri = obverseUri,
            reverseUri = reverseUri
        )
    }

    private suspend fun executeMultiServerFailoverCascade(
        apiKeys: List<String>,
        obverseBase64: String,
        reverseBase64: String,
        onStatusUpdate: (String) -> Unit
    ): String {
        val allCandidateServers = mutableListOf<AnalysisServerEndpoint>()
        var invalidKeyError: InvalidApiKeyException? = null

        for ((index, key) in apiKeys.withIndex()) {
            try {
                val endpointsForKey = buildOrderedEndpointsForKey(
                    apiKey = key,
                    keyLabelPrefix = if (index == 0) "Server" else "Backup-Server"
                )
                allCandidateServers.addAll(endpointsForKey)
            } catch (e: InvalidApiKeyException) {
                Log.w(TAG, "Key #${index + 1} rejected during endpoint discovery", e)
                invalidKeyError = e
            }
        }

        if (allCandidateServers.isEmpty()) {
            throw invalidKeyError ?: InvalidApiKeyException(
                "Dein eingegebener API-Key wurde abgelehnt. Bitte prüfe deinen Schlüssel über das Schlüssel-Symbol oben rechts."
            )
        }

        val now = System.currentTimeMillis()
        val stickyId = stickyHealthyEndpointId

        // Prioritize: 1) Sticky healthy server, 2) Non-cooldown servers in speed/stability order, 3) Cooldown servers as last resort
        val prioritizedServers = allCandidateServers
            .sortedWith(
                compareBy<AnalysisServerEndpoint> { endpoint ->
                    val onCooldown = (serverCooldownUntilMs[endpoint.id] ?: 0L) > now
                    when {
                        !onCooldown && endpoint.id == stickyId -> 0
                        !onCooldown -> 1
                        else -> 2
                    }
                }
            )
            .take(MAX_ENDPOINTS_TO_ATTEMPT)

        // Pre-build payloads lazily so switching between servers takes 0ms
        val geminiSchemaPayload by lazy {
            buildGeminiPayloadJson(obverseBase64, reverseBase64, includeSchema = true)
        }
        val geminiPlainPromptPayload by lazy {
            buildGeminiPayloadJson(obverseBase64, reverseBase64, includeSchema = false)
        }

        val rejectedKeys = HashSet<Int>()
        val failureReasons = mutableListOf<String>()
        var attemptNumber = 0

        for (endpoint in prioritizedServers) {
            val keyHash = endpoint.apiKey.hashCode()
            if (keyHash in rejectedKeys) continue

            attemptNumber++
            if (attemptNumber == 1) {
                onStatusUpdate("Analysiere auf schnellem ${endpoint.displayLabel}…")
            } else {
                onStatusUpdate(
                    "Vorheriger Server ausgelastet – wechsle sofort auf ${endpoint.displayLabel}…"
                )
            }

            try {
                val outcome = withTimeoutOrNull(endpoint.timeoutMs) {
                    callEndpoint(
                        endpoint = endpoint,
                        geminiSchemaPayload = { geminiSchemaPayload },
                        geminiPlainPromptPayload = { geminiPlainPromptPayload },
                        obverseBase64 = obverseBase64,
                        reverseBase64 = reverseBase64
                    )
                }

                if (outcome == null) {
                    // Server was too slow (>18s) -> cooldown & immediately jump to next server!
                    Log.w(TAG, "Server ${endpoint.id} exceeded ${endpoint.timeoutMs}ms fast budget; switching immediately")
                    markServerOnCooldown(endpoint.id)
                    failureReasons.add("${endpoint.displayLabel}: Zeitlimit überschritten")
                    continue
                }

                val parsedResult = evaluateEndpointOutcome(endpoint, outcome)
                if (parsedResult != null) {
                    // Success! Remember this fast server for instant future scans
                    stickyHealthyEndpointId = endpoint.id
                    serverCooldownUntilMs.remove(endpoint.id)
                    return parsedResult
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidApiKeyException) {
                rejectedKeys.add(keyHash)
                invalidKeyError = e
                failureReasons.add("${endpoint.displayLabel}: Ungültiger API-Key")
            } catch (e: ServerRetryableException) {
                Log.w(TAG, "Failover from ${endpoint.id}: ${e.message}")
                markServerOnCooldown(endpoint.id)
                failureReasons.add("${endpoint.displayLabel}: ${e.shortReason}")
            } catch (e: IOException) {
                Log.w(TAG, "Network error on ${endpoint.id}, failing over", e)
                markServerOnCooldown(endpoint.id)
                failureReasons.add("${endpoint.displayLabel}: Verbindungsfehler")
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error on ${endpoint.id}, failing over", e)
                markServerOnCooldown(endpoint.id)
                failureReasons.add("${endpoint.displayLabel}: ${e.localizedMessage ?: "Fehler"}")
            }
        }

        if (rejectedKeys.size == apiKeys.size && invalidKeyError != null) {
            throw invalidKeyError
        }

        val summary = failureReasons.takeLast(3).joinToString(" | ")
        throw IllegalStateException(
            "Aktuell sind alle geprüften Analyse-Server stark ausgelastet ($summary). Bitte tippe in wenigen Sekunden auf „Erneut versuchen“ oder hinterlege optional einen zweiten Backup-Key."
        )
    }

    private class ServerRetryableException(
        val shortReason: String,
        message: String = shortReason
    ) : Exception(message)

    private fun markServerOnCooldown(endpointId: String) {
        serverCooldownUntilMs[endpointId] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
        if (stickyHealthyEndpointId == endpointId) {
            stickyHealthyEndpointId = null
        }
    }

    private suspend fun buildOrderedEndpointsForKey(
        apiKey: String,
        keyLabelPrefix: String
    ): List<AnalysisServerEndpoint> {
        return when {
            apiKey.startsWith("gsk_") -> buildGroqEndpoints(apiKey, keyLabelPrefix)
            apiKey.startsWith("sk-or-") -> buildOpenRouterEndpoints(apiKey, keyLabelPrefix)
            apiKey.startsWith("sk-") -> buildOpenAiEndpoints(apiKey, keyLabelPrefix)
            else -> buildGoogleGeminiClusterEndpoints(apiKey, keyLabelPrefix)
        }
    }

    private suspend fun buildGoogleGeminiClusterEndpoints(
        apiKey: String,
        labelPrefix: String
    ): List<AnalysisServerEndpoint> {
        val rankedModels = getOrDiscoverRankedGeminiModels(apiKey)
        val endpoints = mutableListOf<AnalysisServerEndpoint>()

        rankedModels.forEachIndexed { idx, modelName ->
            val isPro = modelName.contains("pro")
            val clusterType = when {
                modelName.contains("lite") -> "Highspeed-Cluster"
                isPro -> "Pro-Reserve-Cluster"
                else -> "Flash-Cluster"
            }
            endpoints.add(
                AnalysisServerEndpoint(
                    id = "google_v1beta_${apiKey.hashCode()}_$modelName",
                    displayLabel = "$labelPrefix ${idx + 1} ($clusterType)",
                    url = "$GEMINI_V1BETA_BASE/models/$modelName:generateContent",
                    modelId = modelName,
                    apiKey = apiKey,
                    protocol = EndpointProtocol.GEMINI_V1BETA_SCHEMA,
                    timeoutMs = if (isPro) PRO_SERVER_TIMEOUT_MS else FAST_SERVER_TIMEOUT_MS
                )
            )
        }

        // Also add stable v1 gateway backup for the top non-preview Flash model in case v1beta gateway has 502/503
        val topStableFlash = rankedModels.firstOrNull {
            it.contains("flash") && !it.contains("preview") && !it.contains("exp") && !it.contains("latest")
        }
        if (topStableFlash != null) {
            endpoints.add(
                AnalysisServerEndpoint(
                    id = "google_v1_${apiKey.hashCode()}_$topStableFlash",
                    displayLabel = "$labelPrefix V1-Gateway ($topStableFlash)",
                    url = "$GEMINI_V1_BASE/models/$topStableFlash:generateContent",
                    modelId = topStableFlash,
                    apiKey = apiKey,
                    protocol = EndpointProtocol.GEMINI_V1_JSON_PROMPT,
                    timeoutMs = FAST_SERVER_TIMEOUT_MS
                )
            )
        }

        return endpoints
    }

    private fun buildGroqEndpoints(
        apiKey: String,
        labelPrefix: String
    ): List<AnalysisServerEndpoint> {
        val models = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct" to "Groq LPU Vision 1",
            "meta-llama/llama-4-maverick-17b-128e-instruct" to "Groq LPU Vision 2"
        )
        return models.map { (modelId, label) ->
            AnalysisServerEndpoint(
                id = "groq_${apiKey.hashCode()}_$modelId",
                displayLabel = "$labelPrefix ($label)",
                url = GROQ_OPENAI_URL,
                modelId = modelId,
                apiKey = apiKey,
                protocol = EndpointProtocol.OPENAI_COMPATIBLE_VISION,
                timeoutMs = FAST_SERVER_TIMEOUT_MS
            )
        }
    }

    private fun buildOpenRouterEndpoints(
        apiKey: String,
        labelPrefix: String
    ): List<AnalysisServerEndpoint> {
        val models = listOf(
            "google/gemini-3-flash-preview" to "OpenRouter Gemini-Cluster",
            "openai/gpt-4o-mini" to "OpenRouter Vision-Cluster",
            "meta-llama/llama-4-scout:free" to "OpenRouter Free-Cluster"
        )
        return models.map { (modelId, label) ->
            AnalysisServerEndpoint(
                id = "openrouter_${apiKey.hashCode()}_$modelId",
                displayLabel = "$labelPrefix ($label)",
                url = OPENROUTER_URL,
                modelId = modelId,
                apiKey = apiKey,
                protocol = EndpointProtocol.OPENAI_COMPATIBLE_VISION,
                timeoutMs = FAST_SERVER_TIMEOUT_MS
            )
        }
    }

    private fun buildOpenAiEndpoints(
        apiKey: String,
        labelPrefix: String
    ): List<AnalysisServerEndpoint> {
        val models = listOf(
            "gpt-4o-mini" to "OpenAI Fast-Vision",
            "gpt-4.1-mini" to "OpenAI Reserve-Vision"
        )
        return models.map { (modelId, label) ->
            AnalysisServerEndpoint(
                id = "openai_${apiKey.hashCode()}_$modelId",
                displayLabel = "$labelPrefix ($label)",
                url = OPENAI_URL,
                modelId = modelId,
                apiKey = apiKey,
                protocol = EndpointProtocol.OPENAI_COMPATIBLE_VISION,
                timeoutMs = FAST_SERVER_TIMEOUT_MS
            )
        }
    }

    private suspend fun getOrDiscoverRankedGeminiModels(apiKey: String): List<String> {
        val keyHash = apiKey.hashCode()
        val now = System.currentTimeMillis()
        cachedGeminiModelsByKeyHash[keyHash]?.let { cached ->
            if (now - cached.fetchedAtMs < MODEL_LIST_CACHE_TTL_MS && cached.rankedModels.isNotEmpty()) {
                return cached.rankedModels
            }
        }

        val fallbackStaticPool = listOf(
            "gemini-flash-lite-latest",
            "gemini-flash-latest",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite-preview",
            "gemini-3-flash-preview",
            "gemini-pro-latest"
        )

        val request = Request.Builder()
            .url("$GEMINI_V1BETA_BASE/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        val outcome = withTimeoutOrNull(MODEL_DISCOVERY_TIMEOUT_MS) {
            try {
                executeHttpAsync(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return fallbackStaticPool

        if (outcome.httpCode == 400 || outcome.httpCode == 401) {
            val errMsg = extractApiErrorMessage(outcome.body)
            if (outcome.httpCode == 401 ||
                outcome.body.contains("API_KEY_INVALID", ignoreCase = true) ||
                errMsg.contains("API key not valid", ignoreCase = true)
            ) {
                throw InvalidApiKeyException(
                    "Dein eingegebener Google API-Key ist ungültig (${outcome.httpCode}). Bitte prüfe ihn über das Schlüssel-Symbol oben rechts."
                )
            }
        }

        if (outcome.httpCode !in 200..299 || outcome.body.isBlank()) {
            return fallbackStaticPool
        }

        return try {
            val root = JSONObject(outcome.body)
            val modelsArray = root.optJSONArray("models") ?: return fallbackStaticPool
            val discovered = mutableListOf<String>()

            for (i in 0 until modelsArray.length()) {
                val item = modelsArray.optJSONObject(i) ?: continue
                val name = item.optString("name", "").removePrefix("models/")
                if (!name.startsWith("gemini-")) continue
                if (name.startsWith("gemini-1.")) continue
                if (name.contains("image") ||
                    name.contains("tts") ||
                    name.contains("embedding") ||
                    name.contains("robotics") ||
                    name.contains("computer-use") ||
                    name.contains("thinking") ||
                    name.contains("learnlm") ||
                    name.contains("aqa") ||
                    name.contains("audio") ||
                    name.contains("live")
                ) {
                    continue
                }

                val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
                var canGenerate = false
                for (m in 0 until methods.length()) {
                    if (methods.optString(m) == "generateContent") {
                        canGenerate = true
                        break
                    }
                }
                if (canGenerate) {
                    discovered.add(name)
                }
            }

            if (discovered.isEmpty()) {
                return fallbackStaticPool
            }

            // Rank models for MAXIMUM availability & LOWEST latency (avoid hitting overloaded preview clusters first!):
            // Tier 0: Stable (non-preview, non-exp) Flash & Flash-Lite models (highest capacity, fastest response!)
            // Tier 1: -latest aliases (gemini-flash-lite-latest, gemini-flash-latest)
            // Tier 2: Flash-Lite preview models (high throughput cluster)
            // Tier 3: Standard Flash preview models (often higher demand, great secondary cluster)
            // Tier 4: Stable Pro models (separate cluster)
            // Tier 5: Preview Pro models
            val ranked = discovered
                .distinct()
                .sortedWith(
                    compareBy<String> { modelServerPriorityTier(it) }
                        .thenByDescending { extractMajorMinorScore(it) }
                )

            cachedGeminiModelsByKeyHash[keyHash] = CachedModelList(
                fetchedAtMs = now,
                rankedModels = ranked
            )
            ranked
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing /models list, using fallback static pool", e)
            fallbackStaticPool
        }
    }

    private fun modelServerPriorityTier(name: String): Int {
        val isPreviewOrExp = name.contains("preview") || name.contains("exp")
        val isLatestAlias = name.endsWith("-latest")
        val isFlashLite = name.contains("flash-lite") || name.contains("lite")
        val isFlash = name.contains("flash")
        val isPro = name.contains("pro")

        return when {
            isFlash && !isPreviewOrExp && !isLatestAlias && !isFlashLite -> 0 // Stable production Flash (accurate + huge capacity)
            isFlashLite && !isPreviewOrExp && !isLatestAlias -> 1             // Stable production Flash-Lite (ultra fast + huge capacity)
            isLatestAlias && isFlash -> 2                                     // Official Google -latest load-balanced alias
            isFlashLite && isPreviewOrExp -> 3                                // Preview Flash-Lite
            isFlash && isPreviewOrExp -> 4                                    // Preview Flash (frequently experiences high demand -> tried after stable!)
            isPro && !isPreviewOrExp -> 5                                     // Stable Pro cluster
            isPro -> 6                                                        // Preview Pro cluster
            else -> 7
        }
    }

    private fun extractMajorMinorScore(modelName: String): Int {
        val versionToken = modelName.removePrefix("gemini-")
            .takeWhile { it.isDigit() || it == '.' }
        val parts = versionToken.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major * 10 + minor
    }

    private data class HttpOutcome(
        val httpCode: Int,
        val body: String
    )

    private suspend fun callEndpoint(
        endpoint: AnalysisServerEndpoint,
        geminiSchemaPayload: () -> String,
        geminiPlainPromptPayload: () -> String,
        obverseBase64: String,
        reverseBase64: String
    ): HttpOutcome {
        val request = when (endpoint.protocol) {
            EndpointProtocol.GEMINI_V1BETA_SCHEMA -> {
                Request.Builder()
                    .url(endpoint.url)
                    .header("x-goog-api-key", endpoint.apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .post(geminiSchemaPayload().toRequestBody(jsonMediaType))
                    .build()
            }

            EndpointProtocol.GEMINI_V1_JSON_PROMPT -> {
                Request.Builder()
                    .url(endpoint.url)
                    .header("x-goog-api-key", endpoint.apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .post(geminiPlainPromptPayload().toRequestBody(jsonMediaType))
                    .build()
            }

            EndpointProtocol.OPENAI_COMPATIBLE_VISION -> {
                val openAiBody = buildOpenAiCompatibleVisionPayload(
                    modelId = endpoint.modelId,
                    obverseBase64 = obverseBase64,
                    reverseBase64 = reverseBase64
                )
                Request.Builder()
                    .url(endpoint.url)
                    .header("Authorization", "Bearer ${endpoint.apiKey}")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .post(openAiBody.toRequestBody(jsonMediaType))
                    .build()
            }
        }

        return executeHttpAsync(request)
    }

    private fun evaluateEndpointOutcome(
        endpoint: AnalysisServerEndpoint,
        outcome: HttpOutcome
    ): String? {
        val code = outcome.httpCode
        val rawBody = outcome.body

        if (code !in 200..299) {
            val apiErr = extractApiErrorMessage(rawBody)

            // True invalid API key -> immediately abort this key without trying more models on same key
            if (code == 401 ||
                (code == 400 && (rawBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                    apiErr.contains("API key not valid", ignoreCase = true)))
            ) {
                throw InvalidApiKeyException(
                    "Dein eingegebener API-Key wurde als ungültig abgelehnt ($code). Bitte prüfe deinen Schlüssel."
                )
            }

            // Overloaded / High Demand / Rate Limited / Gateway Busy / Model Unavailable -> Instant Failover!
            val shortReason = when (code) {
                503 -> "Server überlastet (503)"
                429 -> "Ratenlimit erreicht (429)"
                404 -> "Modell nicht verfügbar (404)"
                403 -> "Modell gesperrt (403)"
                500, 502, 504 -> "Serverfehler ($code)"
                else -> "HTTP $code"
            }
            throw ServerRetryableException(shortReason = shortReason, message = "$shortReason: $apiErr")
        }

        if (rawBody.isBlank()) {
            throw ServerRetryableException("Leere Serverantwort")
        }

        val extractedText = when (endpoint.protocol) {
            EndpointProtocol.GEMINI_V1BETA_SCHEMA,
            EndpointProtocol.GEMINI_V1_JSON_PROMPT -> extractGeminiCandidateText(rawBody)
            EndpointProtocol.OPENAI_COMPATIBLE_VISION -> extractOpenAiChoiceText(rawBody)
        }

        // Validate that the extracted text actually parses as JSON with coinTitle before accepting!
        val candidateJsonStr = extractCleanJsonObject(extractedText)
        val parsed = try {
            JSONObject(candidateJsonStr)
        } catch (_: Exception) {
            throw ServerRetryableException("Ungültiges Antwortformat")
        }

        if (!parsed.has("landUndMotiv") && !parsed.has("coinTitle")) {
            throw ServerRetryableException("Unvollständige Antwort")
        }

        return candidateJsonStr
    }

    private fun extractGeminiCandidateText(rawBody: String): String {
        val responseJson = JSONObject(rawBody)
        val candidates = responseJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw ServerRetryableException("Keine Antwortkandidaten")
        }
        val contentObj = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw ServerRetryableException("Leerer Inhalt")
        val parts = contentObj.optJSONArray("parts")
            ?: throw ServerRetryableException("Keine Textteile")

        for (i in parts.length() - 1 downTo 0) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            val text = part.optString("text", "")
            if (text.isNotBlank()) return text
        }
        throw ServerRetryableException("Kein Auswertungstext")
    }

    private fun extractOpenAiChoiceText(rawBody: String): String {
        val responseJson = JSONObject(rawBody)
        val choices = responseJson.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw ServerRetryableException("Keine Antwort erhalten")
        }
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw ServerRetryableException("Leere Nachricht")
        val content = message.optString("content", "")
        if (content.isBlank()) {
            throw ServerRetryableException("Leerer Antworttext")
        }
        return content
    }

    private suspend fun executeHttpAsync(request: Request): HttpOutcome =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val bodyString = try {
                            resp.body?.string().orEmpty()
                        } catch (e: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                            return
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                HttpOutcome(
                                    httpCode = resp.code,
                                    body = bodyString
                                )
                            )
                        }
                    }
                }
            })
        }

    private fun extractCleanJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }

    private fun extractApiErrorMessage(rawBody: String): String {
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun numismaticPromptInstructions(): String = """
        Du bist ein erfahrener Numismatiker und Experte für europäische 2-Euro-Münzen (reguläre Umlaufmünzen, Gedenkmünzen, Kleinstaaten Monaco/Vatikan/San Marino/Andorra sowie echte Fehlprägungen).

        Bild 1 zeigt die Vorderseite (Landesmotiv), Bild 2 die Rückseite (Europakarte / 2 EURO). Falls vertauscht, berücksichtige das sachlich.

        Antworte AUSSCHLIESSLICH als valides JSON-Objekt mit exakt diesen Feldern auf Deutsch (je Abschnitt präzise, sachlich, 2–4 Sätze):
        {
          "isValidTwoEuroCoin": true,
          "coinTitle": "Kurzer Titel der Münze",
          "countryName": "Ausgabeland auf Deutsch",
          "mintMark": "Prägebuchstabe / Münzzeichen oder 'Nicht eindeutig erkennbar'",
          "coinCategory": "Reguläre Umlaufmünze | 2-Euro-Gedenkmünze | Kleinstaat-Rarität",
          "rarityLevel": "Häufig (Massenauflage) | Mäßig selten | Selten | Sehr selten / Rarität",
          "conditionGrade": "Stark im Umlauf | Gut erhalten | Sehr gut / Vorzüglich | Stempelglanz / Unzirkuliert",
          "estimatedValueRangeEur": "z. B. '2,00 € (Nennwert)' oder '3,50 € – 5,00 €'",
          "landUndMotiv": "1. Land & Motiv: Ausgabeland, spezifisches Motiv, Prägestätte und Kartentyp.",
          "praegejahr": "2. Prägejahr: Auf der Münze angegebenes Jahr.",
          "auflageUndSeltenheit": "3. Auflage & Seltenheit: Umlaufmünze vs. seltene Gedenkmünze/Kleinstaat inkl. ungefährer Auflage falls bekannt.",
          "zustandUndErhaltung": "4. Zustand & Erhaltung: Sichtbare Erhaltungseinschätzung mit klarem Hinweis, dass Kratzer den Sammlerwert drastisch mindern.",
          "realistischerMarktwert": "5. Realistischer Marktwert: Realistischer Sammler-/Marktwert in Euro für den aktuellen Zustand.",
          "hinweisFakePreise": "6. Wichtiger Hinweis zu Fake-Preisen: Warnung vor astronomischen Mondpreisen auf eBay für angebliche Fehlprägungen und kurze Erklärung, worauf es bei echten Fehlprägungen (Spiegelei, Monometall, Zainende, starke Stempeldrehung) ankommt."
        }
    """.trimIndent()

    private fun buildGeminiPayloadJson(
        obverseBase64: String,
        reverseBase64: String,
        includeSchema: Boolean
    ): String {
        val partsArray = JSONArray()
            .put(JSONObject().put("text", "Bild 1 – Vorderseite (Landesmotiv):"))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", obverseBase64)
                )
            )
            .put(JSONObject().put("text", "Bild 2 – Rückseite (Europakarte):"))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", reverseBase64)
                )
            )
            .put(JSONObject().put("text", numismaticPromptInstructions()))

        val contentsArray = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", partsArray)
        )

        val generationConfig = JSONObject().put("temperature", 0.2)
        if (includeSchema) {
            generationConfig.put("responseMimeType", "application/json")
            generationConfig.put("responseSchema", buildFastResponseSchemaJson())
        }

        return JSONObject()
            .put("contents", contentsArray)
            .put("generationConfig", generationConfig)
            .toString()
    }

    private fun buildOpenAiCompatibleVisionPayload(
        modelId: String,
        obverseBase64: String,
        reverseBase64: String
    ): String {
        val contentArray = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Bild 1 – Vorderseite (Landesmotiv):"))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$obverseBase64")
                    )
            )
            .put(JSONObject().put("type", "text").put("text", "Bild 2 – Rückseite (Europakarte):"))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$reverseBase64")
                    )
            )
            .put(JSONObject().put("type", "text").put("text", numismaticPromptInstructions()))

        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", contentArray)
        )

        return JSONObject()
            .put("model", modelId)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", messages)
            .toString()
    }

    private fun buildFastResponseSchemaJson(): JSONObject {
        val stringProp = { JSONObject().put("type", "STRING") }
        val properties = JSONObject()
            .put("isValidTwoEuroCoin", JSONObject().put("type", "BOOLEAN"))
            .put("coinTitle", stringProp())
            .put("countryName", stringProp())
            .put("mintMark", stringProp())
            .put("coinCategory", stringProp())
            .put("rarityLevel", stringProp())
            .put("conditionGrade", stringProp())
            .put("estimatedValueRangeEur", stringProp())
            .put("landUndMotiv", stringProp())
            .put("praegejahr", stringProp())
            .put("auflageUndSeltenheit", stringProp())
            .put("zustandUndErhaltung", stringProp())
            .put("realistischerMarktwert", stringProp())
            .put("hinweisFakePreise", stringProp())

        val requiredFields = JSONArray(
            listOf(
                "isValidTwoEuroCoin",
                "coinTitle",
                "countryName",
                "mintMark",
                "coinCategory",
                "rarityLevel",
                "conditionGrade",
                "estimatedValueRangeEur",
                "landUndMotiv",
                "praegejahr",
                "auflageUndSeltenheit",
                "zustandUndErhaltung",
                "realistischerMarktwert",
                "hinweisFakePreise"
            )
        )

        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", properties)
            .put("required", requiredFields)
    }

    private fun encodeBitmapToJpegBase64(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap {
        val original: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > MAX_IMAGE_DIMENSION_PX) {
                    val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: throw IOException("Bilddatei konnte nicht geöffnet werden.")

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) {
                throw IOException("Ungültiges Bildformat.")
            }

            var sampleSize = 1
            while (maxOf(width / sampleSize, height / sampleSize) > MAX_IMAGE_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IOException("Bild konnte nicht dekodiert werden.")

            scaleDownIfNeeded(sampled)
        }

        return scaleDownIfNeeded(original)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_IMAGE_DIMENSION_PX) return bitmap
        val ratio = MAX_IMAGE_DIMENSION_PX.toFloat() / longest.toFloat()
        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
