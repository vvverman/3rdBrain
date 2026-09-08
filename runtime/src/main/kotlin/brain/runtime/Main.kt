package brain.runtime

import brain.model.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.nio.file.Path

private class LocalAccessDenied : IllegalArgumentException("Доступ разрешён только локальному приложению")
private const val MAX_AUDIO = 64L * 1024 * 1024
private val localOrigins = setOf("http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:8787", "http://127.0.0.1:8787")
private val LocalAccess = createApplicationPlugin("LocalAccess") {
    onCall { call ->
        val host = call.request.headers[HttpHeaders.Host]?.lowercase()?.substringBefore(':')
        if (host != null && host !in setOf("localhost", "127.0.0.1")) throw LocalAccessDenied()
        val origin = call.request.headers[HttpHeaders.Origin]
        if (origin != null && origin !in localOrigins) throw LocalAccessDenied()
        if (call.request.headers["Sec-Fetch-Site"] == "cross-site" && origin == null) throw LocalAccessDenied()
        if (call.request.httpMethod in setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete)
            && call.request.headers["X-3rdBrain-Client"] != "web") throw LocalAccessDenied()
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(length == null || length <= MAX_AUDIO + 65536) { "Запрос слишком большой" }
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Cache-Control", "no-store")
    }
}

fun main() {
    val root = Path.of(System.getenv("THIRDBRAIN_HOME") ?: Path.of(System.getProperty("user.home"), ".3rdbrain").toString())
    lateinit var processing: LocalProcessing
    val store = FileBrainStore(root) { processing.status() }
    processing = LocalProcessing(store)
    val webRoot = Path.of(System.getenv("THIRDBRAIN_WEB_ROOT") ?: "composeApp/build/dist/wasmJs/productionExecutable")
    println("3rdBrain: откройте http://127.0.0.1:8787 . Данные: $root")
    embeddedServer(Netty, host = "127.0.0.1", port = 8787) { brainModule(store, processing, webRoot) }.start(wait = true)
}

fun Application.brainModule(store: FileBrainStore, processing: LocalProcessing, webRoot: Path? = null) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    install(StatusPages) {
        exception<LocalAccessDenied> { call, cause -> call.respond(HttpStatusCode.Forbidden, ApiError(cause.message!!)) }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            val expected = cause is IllegalArgumentException || cause is IllegalStateException
            call.respond(if (expected) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError,
                ApiError(if (expected) cause.message ?: "Некорректный запрос" else "Ошибка локального сервиса. Сохранённые источники не удалены"))
        }
    }
    install(LocalAccess)
    install(CORS) {
        allowHost("localhost:8080"); allowHost("127.0.0.1:8080")
        allowHost("localhost:8787"); allowHost("127.0.0.1:8787")
        allowMethod(HttpMethod.Put); allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType); allowHeader("X-3rdBrain-Client"); allowHeader("X-Capture-Id")
        allowNonSimpleContentTypes = true
    }
    routing {
        get("/api/health") { call.respond(mapOf("ok" to true)) }
        get("/api/snapshot") { call.respond(store.snapshot()) }
        post("/api/projects") { call.respond(store.createProject(call.receive<ProjectDraft>())) }
        put("/api/projects/{id}") { call.respond(store.updateProject(call.parameters["id"]!!, call.receive<ProjectUpdate>())) }
        post("/api/projects/{id}/pin") { call.respond(store.pinProject(call.parameters["id"]!!, call.receive<PinRequest>().pinned)) }
        post("/api/pins/order") { call.respond(store.orderPins(call.receive<PinOrderRequest>().ids)) }
        post("/api/captures/audio") {
            var fileName = "capture.webm"; var bytes: ByteArray? = null
            val multipart = call.receiveMultipart(formFieldLimit = MAX_AUDIO)
            multipart.forEachPart { part ->
                try {
                    if (part is PartData.FileItem) {
                        require(bytes == null) { "Передайте одну запись" }
                        fileName = part.originalFileName ?: fileName
                        bytes = part.provider().readRemaining(MAX_AUDIO + 1).readByteArray()
                        require(bytes!!.size <= MAX_AUDIO) { "Допустима запись до 64 МБ" }
                    }
                } finally { part.dispose() }
            }
            val capture = store.createCapture(fileName, bytes ?: error("Аудиофайл не передан"), call.request.headers["X-Capture-Id"])
            if (capture.status == CaptureStatus.QUEUED) processing.enqueue(capture.id, this@brainModule)
            call.respond(HttpStatusCode.Created, store.capture(capture.id)!!)
        }
        get("/api/captures/{id}/audio") {
            val capture = store.capture(call.parameters["id"]!!) ?: error("Запись не найдена")
            call.respondFile(store.resolveAudio(capture, call.request.queryParameters["compact"] == "true").toFile())
        }
        put("/api/notes/{id}") { call.respond(store.updateNote(call.parameters["id"]!!, call.receive<NoteUpdate>())) }
        put("/api/captures/{id}/draft") { call.respond(store.updateDraft(call.parameters["id"]!!, call.receive<CaptureDraftUpdate>())) }
        post("/api/captures/{id}/process") { call.respond(processing.enqueue(call.parameters["id"]!!, this@brainModule)) }
        post("/api/captures/{id}/distribute") { call.respond(store.distribute(call.parameters["id"]!!, call.receive<DistributionRequest>())) }
        if (webRoot != null) staticFiles("/", webRoot.toFile())
    }
}
