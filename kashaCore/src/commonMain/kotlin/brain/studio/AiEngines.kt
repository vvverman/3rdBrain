package brain.studio

import brain.model.Project
import kotlinx.serialization.Serializable

/** Роли ИИ в продукте. Core не знает конкретных моделей или провайдеров. */
@Serializable
enum class AiRole { SPEECH_TO_TEXT, TEXT, ROUTING }

@Serializable
enum class AiLocality { LOCAL, CLOUD, NATIVE }

/** Какие пользовательские данные потенциально покидают устройство. */
@Serializable
enum class AiDataKind { AUDIO, NOTE_TEXT, PROJECT_TITLES, PROJECT_INSTRUCTIONS }

@Serializable
data class AiEngineDescriptor(
    val id: String,
    val name: String,
    val provider: String,
    val roles: Set<AiRole>,
    val locality: AiLocality,
    val version: String = "",
    val approximateSizeMb: Int? = null,
    val languages: List<String> = emptyList(),
    val defaultInstalled: Boolean = false,
    val installable: Boolean = false,
    val description: String = "",
) {
    fun supports(role: AiRole): Boolean = role in roles
    val isExternal: Boolean get() = locality == AiLocality.CLOUD
}

@Serializable
data class AiSelection(
    val speechToText: String = AiCatalog.DEFAULT_STT,
    val text: String = AiCatalog.DEFAULT_TEXT,
    val routing: String = AiCatalog.DEFAULT_ROUTING,
) {
    fun engineId(role: AiRole): String = when (role) {
        AiRole.SPEECH_TO_TEXT -> speechToText
        AiRole.TEXT -> text
        AiRole.ROUTING -> routing
    }

    fun with(role: AiRole, engineId: String): AiSelection = when (role) {
        AiRole.SPEECH_TO_TEXT -> copy(speechToText = engineId)
        AiRole.TEXT -> copy(text = engineId)
        AiRole.ROUTING -> copy(routing = engineId)
    }

    fun validated(): AiSelection {
        AiRole.entries.forEach { role ->
            require(AiCatalog.supportsSelection(engineId(role), role)) {
                "AI engine ${engineId(role)} does not support $role"
            }
        }
        return this
    }
}

@Serializable
data class CloudProviderDescriptor(
    val id: String,
    val name: String,
    val roles: Set<AiRole>,
    val endpointRequired: Boolean = false,
    val description: String = "",
)

/**
 * Метаданные подключения. API key здесь намеренно отсутствует.
 * Один credential провайдера может использовать разные модели для разных ролей.
 */
@Serializable
data class CloudAiConnection(
    val providerId: String,
    val modelIds: Map<AiRole, String> = emptyMap(),
    val endpoint: String? = null,
    val enabled: Boolean = false,
    val privacyConsentVersion: Int = 0,
) {
    fun modelFor(role: AiRole): String? = modelIds[role]?.trim()?.takeIf { it.isNotEmpty() }
    val roles: Set<AiRole> get() = modelIds.filterValues { it.isNotBlank() }.keys
}

@Serializable
data class AiPackageState(
    val engineId: String,
    val installed: Boolean,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
)

interface AiPackageGateway {
    val available: Boolean
    suspend fun states(): List<AiPackageState>
    suspend fun install(engineId: String)
    suspend fun remove(engineId: String)
}

object NoopAiPackageGateway : AiPackageGateway {
    override val available = false
    override suspend fun states(): List<AiPackageState> = AiCatalog.engines
        .filter { it.locality != AiLocality.CLOUD }
        .map { AiPackageState(it.id, installed = it.defaultInstalled) }
    override suspend fun install(engineId: String) = error("AI package installation is unavailable on this platform")
    override suspend fun remove(engineId: String) = error("AI package installation is unavailable on this platform")
}

/**
 * Платформенный шлюз облачных подключений. Реализация ОБЯЗАНА хранить API key
 * в защищённом системном хранилище. Core получает только метаданные подключения.
 */
interface CloudAiGateway {
    val available: Boolean
    suspend fun connections(): List<CloudAiConnection>
    suspend fun save(connection: CloudAiConnection, apiKey: String?)
    suspend fun remove(providerId: String)
    suspend fun test(connection: CloudAiConnection, apiKey: String?): Boolean
}

object NoopCloudAiGateway : CloudAiGateway {
    override val available = false
    override suspend fun connections(): List<CloudAiConnection> = emptyList()
    override suspend fun save(connection: CloudAiConnection, apiKey: String?) = error("Cloud AI is unavailable on this platform")
    override suspend fun remove(providerId: String) = Unit
    override suspend fun test(connection: CloudAiConnection, apiKey: String?): Boolean = false
}

interface SpeechToTextEngine {
    val descriptor: AiEngineDescriptor
    suspend fun transcribe(file: String, language: String): String
}

interface TextProcessingEngine {
    val descriptor: AiEngineDescriptor
    suspend fun title(text: String, language: String): String
    suspend fun tidy(text: String, language: String): String
}

interface RoutingEngine {
    val descriptor: AiEngineDescriptor
    suspend fun rank(text: String, projects: List<Project>, language: String): Map<String, Int>
}

class CompositeIntelligence(
    private val speech: SpeechToTextEngine,
    private val text: TextProcessingEngine,
    private val routing: RoutingEngine,
    override val simulated: Boolean = false,
) : Intelligence {
    override suspend fun transcribe(file: String, language: String, example: String): String = speech.transcribe(file, language)
    override suspend fun title(text: String, language: String): String = this.text.title(text, language)
    override suspend fun tidy(text: String, language: String): String = this.text.tidy(text, language)
    override suspend fun rank(text: String, projects: List<Project>, language: String): Map<String, Int> = routing.rank(text, projects, language)
}

object AiPrivacy {
    const val CONSENT_VERSION = 1

    fun dataFor(role: AiRole): Set<AiDataKind> = when (role) {
        AiRole.SPEECH_TO_TEXT -> setOf(AiDataKind.AUDIO)
        AiRole.TEXT -> setOf(AiDataKind.NOTE_TEXT)
        AiRole.ROUTING -> setOf(AiDataKind.NOTE_TEXT, AiDataKind.PROJECT_TITLES, AiDataKind.PROJECT_INSTRUCTIONS)
    }

    fun dataFor(roles: Set<AiRole>): Set<AiDataKind> = roles.flatMap(::dataFor).toSet()
}

object AiCatalog {
    const val DEFAULT_STT = "local.whisper.small"
    const val DEFAULT_TEXT = "local.qwen3.4b"
    const val DEFAULT_ROUTING = DEFAULT_TEXT
    private const val CLOUD_PREFIX = "cloud:"

    val engines: List<AiEngineDescriptor> = listOf(
        AiEngineDescriptor(
            id = DEFAULT_STT,
            name = "Whisper Small",
            provider = "OpenAI / whisper.cpp",
            roles = setOf(AiRole.SPEECH_TO_TEXT),
            locality = AiLocality.LOCAL,
            version = "small",
            approximateSizeMb = 500,
            languages = Languages.codes,
            defaultInstalled = true,
            installable = true,
            description = "Базовая локальная транскрибация",
        ),
        AiEngineDescriptor(
            id = "local.whisper.medium",
            name = "Whisper Medium",
            provider = "OpenAI / whisper.cpp",
            roles = setOf(AiRole.SPEECH_TO_TEXT),
            locality = AiLocality.LOCAL,
            version = "medium",
            approximateSizeMb = 1500,
            languages = Languages.codes,
            installable = true,
            description = "Точнее, но тяжелее",
        ),
        AiEngineDescriptor(
            id = "local.whisper.large-v3",
            name = "Whisper Large v3",
            provider = "OpenAI / whisper.cpp",
            roles = setOf(AiRole.SPEECH_TO_TEXT),
            locality = AiLocality.LOCAL,
            version = "large-v3",
            approximateSizeMb = 3100,
            languages = Languages.codes,
            installable = true,
            description = "Максимальная локальная точность",
        ),
        AiEngineDescriptor(
            id = DEFAULT_TEXT,
            name = "Qwen 4B",
            provider = "Qwen / llama.cpp",
            roles = setOf(AiRole.TEXT, AiRole.ROUTING),
            locality = AiLocality.LOCAL,
            version = "4B Q4",
            approximateSizeMb = 2500,
            languages = Languages.codes,
            defaultInstalled = true,
            installable = true,
            description = "Базовая локальная модель Kasha",
        ),
        AiEngineDescriptor(
            id = "local.gemma.4b",
            name = "Gemma 4B",
            provider = "Google / llama.cpp",
            roles = setOf(AiRole.TEXT, AiRole.ROUTING),
            locality = AiLocality.LOCAL,
            version = "4B Q4",
            approximateSizeMb = 3000,
            languages = Languages.codes,
            installable = false,
            description = "Доступна после принятия условий модели Google",
        ),
        AiEngineDescriptor(
            id = "local.qwen.8b",
            name = "Qwen 8B",
            provider = "Qwen / llama.cpp",
            roles = setOf(AiRole.TEXT, AiRole.ROUTING),
            locality = AiLocality.LOCAL,
            version = "8B Q4",
            approximateSizeMb = 5000,
            languages = Languages.codes,
            installable = true,
            description = "Более тяжёлая локальная модель",
        ),
    )

    val cloudProviders: List<CloudProviderDescriptor> = listOf(
        CloudProviderDescriptor("openai", "OpenAI", setOf(AiRole.SPEECH_TO_TEXT, AiRole.TEXT, AiRole.ROUTING), description = "OpenAI API"),
        CloudProviderDescriptor("anthropic", "Anthropic Claude", setOf(AiRole.TEXT, AiRole.ROUTING), description = "Anthropic API"),
        CloudProviderDescriptor("gemini", "Google Gemini", setOf(AiRole.TEXT, AiRole.ROUTING), description = "Google AI API"),
        CloudProviderDescriptor("openrouter", "OpenRouter", setOf(AiRole.TEXT, AiRole.ROUTING), description = "Множество моделей через единый API"),
        CloudProviderDescriptor("openai-compatible", "OpenAI-compatible", setOf(AiRole.TEXT, AiRole.ROUTING), endpointRequired = true, description = "Любой совместимый сервер"),
        CloudProviderDescriptor("custom", "Custom endpoint", AiRole.entries.toSet(), endpointRequired = true, description = "Собственный API-адаптер"),
    )

    fun engine(id: String): AiEngineDescriptor? = engines.firstOrNull { it.id == id }
    fun enginesFor(role: AiRole): List<AiEngineDescriptor> = engines.filter { it.supports(role) }
    fun provider(id: String): CloudProviderDescriptor? = cloudProviders.firstOrNull { it.id == id }

    fun cloudEngineId(providerId: String, role: AiRole): String = "$CLOUD_PREFIX$providerId:${role.name}"

    fun cloudProviderId(engineId: String): String? = if (engineId.startsWith(CLOUD_PREFIX)) {
        engineId.removePrefix(CLOUD_PREFIX).substringBefore(':').takeIf { it.isNotBlank() }
    } else null

    fun cloudRole(engineId: String): AiRole? = if (engineId.startsWith(CLOUD_PREFIX)) {
        runCatching { AiRole.valueOf(engineId.substringAfterLast(':')) }.getOrNull()
    } else null

    fun supportsSelection(engineId: String, role: AiRole): Boolean {
        engine(engineId)?.let { return it.supports(role) }
        val provider = cloudProviderId(engineId)?.let(::provider) ?: return false
        return cloudRole(engineId) == role && role in provider.roles
    }

    fun selectedDescriptor(engineId: String): AiEngineDescriptor? {
        engine(engineId)?.let { return it }
        val provider = cloudProviderId(engineId)?.let(::provider) ?: return null
        val role = cloudRole(engineId) ?: return null
        if (role !in provider.roles) return null
        return AiEngineDescriptor(
            id = engineId,
            name = provider.name,
            provider = provider.name,
            roles = setOf(role),
            locality = AiLocality.CLOUD,
            description = provider.description,
        )
    }

    fun connectedCloudChoices(role: AiRole, connections: List<CloudAiConnection>): List<AiEngineDescriptor> = connections
        .filter { it.enabled && it.privacyConsentVersion >= AiPrivacy.CONSENT_VERSION && it.modelFor(role) != null }
        .mapNotNull { connection ->
            val provider = provider(connection.providerId) ?: return@mapNotNull null
            val model = connection.modelFor(role) ?: return@mapNotNull null
            if (role !in provider.roles) return@mapNotNull null
            AiEngineDescriptor(
                id = cloudEngineId(connection.providerId, role),
                name = "${provider.name} · $model",
                provider = provider.name,
                roles = setOf(role),
                locality = AiLocality.CLOUD,
                description = provider.description,
            )
        }
}
