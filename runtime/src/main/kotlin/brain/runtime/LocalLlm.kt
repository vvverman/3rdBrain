package brain.runtime

import brain.domain.LocalModelText
import java.nio.file.Files
import java.nio.file.Path

/** Единственный платформенный адаптер текстовой модели для текущего локального веба. */
class LocalLlm(private val cli: String, private val model: String, private val root: Path, private val runner: CommandRunner) {
    suspend fun generate(prompt: String, schema: String, tokens: Int): String {
        // Личный текст не попадает в список аргументов, видимый через ps/диспетчер процессов.
        val file = Files.createTempFile(root, ".prompt-", ".txt")
        try {
            Files.writeString(file, prompt)
            return LocalModelText.jsonPayload(runner.run(listOf(cli, "-m", model,
                "--jinja", "--single-turn", "--reasoning", "off", "--no-display-prompt", "--simple-io",
                "--log-disable", "--no-escape", "--file", file.toString(), "--json-schema", schema,
                "-n", tokens.toString(), "-c", "8192", "--temp", "0", "--seed", "0", "-t", "4"), 900))
        } finally { Files.deleteIfExists(file) }
    }
}
