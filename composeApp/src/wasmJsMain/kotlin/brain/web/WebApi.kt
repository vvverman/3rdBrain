package brain.web

import brain.model.AppSnapshot
import brain.model.Capture
import brain.model.CaptureDraftUpdate
import brain.model.DistributionRequest
import brain.model.Note
import brain.model.NoteUpdate
import brain.model.PinRequest
import brain.model.PinOrderRequest
import brain.model.ApiError
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import brain.model.Project
import brain.model.ProjectDraft
import brain.model.ProjectUpdate
import brain.domain.BrainRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class WebBrainRepository(private val baseUrl: String) : BrainRepository {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient(Js) {
        expectSuccess = true
        defaultRequest { header("X-3rdBrain-Client", "web") }
        install(HttpTimeout) { requestTimeoutMillis = 30000 }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ResponseException) {
                    val error = runCatching { codec.decodeFromString<ApiError>(cause.response.bodyAsText()).error }.getOrNull()
                    throw IllegalStateException(error ?: "Локальный сервис вернул ошибку ${cause.response.status.value}")
                }
            }
        }
        install(ContentNegotiation) { json(codec) }
    }

    override suspend fun snapshot(): AppSnapshot = client.get("$baseUrl/api/snapshot").body()
    override suspend fun createProject(draft: ProjectDraft): Project = client.post("$baseUrl/api/projects") { contentType(ContentType.Application.Json); setBody(draft) }.body()
    override suspend fun updateProject(id: String, update: ProjectUpdate): Project = client.put("$baseUrl/api/projects/$id") { contentType(ContentType.Application.Json); setBody(update) }.body()
    override suspend fun pinProject(id: String, pinned: Boolean): Project = client.post("$baseUrl/api/projects/$id/pin") { contentType(ContentType.Application.Json); setBody(PinRequest(pinned)) }.body()
    override suspend fun updateCaptureDraft(id: String, update: CaptureDraftUpdate): Capture = client.put("$baseUrl/api/captures/$id/draft") { contentType(ContentType.Application.Json); setBody(update) }.body()
    override suspend fun distribute(id: String, request: DistributionRequest): Note = client.post("$baseUrl/api/captures/$id/distribute") { contentType(ContentType.Application.Json); setBody(request) }.body()
    override suspend fun updateNote(id: String, update: NoteUpdate): Note = client.put("$baseUrl/api/notes/$id") { contentType(ContentType.Application.Json); setBody(update) }.body()
    override suspend fun orderPins(ids: List<String>) { client.post("$baseUrl/api/pins/order") { contentType(ContentType.Application.Json); setBody(PinOrderRequest(ids)) } }
    override suspend fun reprocess(id: String): Capture = client.post("$baseUrl/api/captures/$id/process").body()
}
