package com.demo.sync

import com.demo.model.QueryNode
import com.google.gson.GsonBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Low-level HTTP client for the SQLFolio sync REST API.
 *
 * ─── Server API contract ────────────────────────────────────────────────────
 *
 *  GET  /api/sqlfolio/health
 *       → 200 { "status": "ok" }
 *
 *  GET  /api/sqlfolio/workspaces/{workspaceId}
 *       Headers: Authorization: Bearer {token}
 *       → 200 { "version": 5, "root": { ...QueryNode tree... } }
 *       → 404 workspace not found (first push will create it)
 *       → 401 bad token
 *
 *  PUT  /api/sqlfolio/workspaces/{workspaceId}
 *       Headers: Authorization: Bearer {token}, Content-Type: application/json
 *       Body:    { "version": 5, "root": { ...QueryNode tree... } }
 *       → 200 { "version": 6, "conflict": false }           — accepted
 *       → 409 { "version": 6, "conflict": true, "root": {…} } — client is behind; server tree returned
 *       → 401 bad token
 *
 * Conflict rule: if the client's `version` < the server's current version,
 * the server MUST return 409 with the latest tree so the client can resolve.
 * ────────────────────────────────────────────────────────────────────────────
 */
object SQLFolioApiClient {

    private val gson = GsonBuilder().create()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // ── Response / request data classes ───────────────────────────────────────

    /** Snapshot returned by the server on a successful GET. */
    data class WorkspaceSnapshot(
        val version: Int = 0,
        val root: QueryNode = QueryNode(name = "Root", isFolder = true)
    )

    /** Body sent to the server on a PUT. */
    private data class PushRequest(val version: Int, val root: QueryNode)

    /** Body returned by the server after a PUT (conflict or not). */
    data class PushResponse(
        val version: Int = 0,
        val conflict: Boolean = false,
        val root: QueryNode? = null      // only present when conflict == true
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /** Fetch the latest workspace state from the server. */
    @Throws(Exception::class)
    fun pull(serverUrl: String, apiToken: String, workspaceId: String): WorkspaceSnapshot {
        val url = "${serverUrl.trimEnd('/')}/api/sqlfolio/workspaces/$workspaceId"
        val raw = get(url, apiToken)
        return when (raw.status) {
            200  -> gson.fromJson(raw.body, WorkspaceSnapshot::class.java)
                    ?: throw RuntimeException("Empty response from server")
            404  -> WorkspaceSnapshot()   // workspace not created yet
            401  -> throw RuntimeException("Unauthorized — check your API token")
            else -> throw RuntimeException("Server returned HTTP ${raw.status}: ${raw.body.take(200)}")
        }
    }

    /** Push the local tree to the server. Returns the server's response (may be a conflict). */
    @Throws(Exception::class)
    fun push(
        serverUrl: String,
        apiToken: String,
        workspaceId: String,
        version: Int,
        root: QueryNode
    ): PushResponse {
        val url  = "${serverUrl.trimEnd('/')}/api/sqlfolio/workspaces/$workspaceId"
        val body = gson.toJson(PushRequest(version, root))
        val raw  = put(url, apiToken, body)
        return when (raw.status) {
            200, 201 -> gson.fromJson(raw.body, PushResponse::class.java) ?: PushResponse(version)
            409      -> gson.fromJson(raw.body, PushResponse::class.java)
                        ?: throw RuntimeException("Conflict but server returned no data")
            401      -> throw RuntimeException("Unauthorized — check your API token")
            else     -> throw RuntimeException("Server returned HTTP ${raw.status}: ${raw.body.take(200)}")
        }
    }

    /** Quick reachability + auth check used by the Settings panel "Test Connection" button. */
    @Throws(Exception::class)
    fun testConnection(serverUrl: String, apiToken: String): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sqlfolio/health"
        val raw = get(url, apiToken)
        return raw.status in 200..299
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private data class RawResponse(val status: Int, val body: String)

    private fun get(url: String, token: String): RawResponse {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(15))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return RawResponse(resp.statusCode(), resp.body() ?: "")
    }

    private fun put(url: String, token: String, body: String): RawResponse {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(15))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return RawResponse(resp.statusCode(), resp.body() ?: "")
    }
}

