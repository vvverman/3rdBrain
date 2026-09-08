package brain.runtime

import brain.model.CaptureDraftUpdate
import brain.model.DistributionRequest
import brain.model.NoteUpdate
import brain.model.PinRequest
import brain.model.ProjectDraft
import brain.model.ProjectUpdate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readByteArray
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.file.Path

fun main() {
    val root = Path.of(System.getenv("THIRDBRAIN_HOME") ?: Path.of(System.getProperty("user.home"), ".3rdbrain").toString())
    lateinit var processing: LocalProcessing
    val store = FileBrainStore(root) { processing.status() }
    processing = LocalProcessing(store)
    embeddedServer(Netty, host = "127.0.0.1", port = 8787) { brainModule(store, processing) }.start(wait = true)
}

fun Application.brainModule(store: FileBrainStore, processing: LocalProcessing) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(CORS) {
        anyHost()
        allowNonSimpleContentTypes = true
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Ошибка"))) }
    }
    routing {
        get("/api/health") { call.respond(mapOf("ok" to true)) }
        get("/api/snapshot") { call.respond(store.snapshot()) }
        post("/api/projects") { call.respond(store.createProject(call.receive<ProjectDraft>())) }
        put("/api/projects/{id}") { call.respond(store.updateProject(call.parameters["id"] ?: error("Нет id"), call.receive<ProjectUpdate>())) }
        post("/api/projects/{id}/pin") {
            val id = call.parameters["id"] ?: error("Нет id")
            call.respond(store.pinProject(id, call.receive<PinRequest>().pinned))
        }

        post("/api/captures/audio") {
            var fileName = "capture.webm"
            var bytes: ByteArray? = null
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileName = part.originalFileName ?: fileName
                    bytes = part.provider().readRemaining().readByteArray()
                }
                part.dispose()
            }
            val payload = bytes ?: error("Аудиофайл не передан")
            require(payload.size <= 64 * 1024 * 1024) { "Аудиозапись слишком большая для web-MVP" }
            val capture = store.createCapture(fileName, payload)
            scope.launch { processing.process(capture.id) }
            call.respond(HttpStatusCode.Created, capture)
        }

        get("/api/captures/{id}/audio") {
            val capture = store.capture(call.parameters["id"] ?: error("Нет id")) ?: error("Запись не найдена")
            call.respondFile(store.resolveAudio(capture).toFile())
        }
        put("/api/notes/{id}") { call.respond(store.updateNote(call.parameters["id"] ?: error("Нет id"), call.receive<NoteUpdate>())) }
        put("/api/captures/{id}/draft") { call.respond(store.updateDraft(call.parameters["id"] ?: error("Нет id"), call.receive<CaptureDraftUpdate>())) }
        post("/api/captures/{id}/process") { call.respond(processing.process(call.parameters["id"] ?: error("Нет id"))) }
        post("/api/captures/{id}/distribute") { call.respond(store.distribute(call.parameters["id"] ?: error("Нет id"), call.receive<DistributionRequest>())) }
    }
}
