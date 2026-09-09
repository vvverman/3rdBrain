package brain.domain

import brain.model.Project
import kotlinx.serialization.json.*
import kotlin.test.*

class LocalModelTextTest {
    @Test fun numbersCannotBeLostOrInvented() {
        assertFails { LocalModelText.requirePreserved("До 15:30, бюджет 25,5", "До 15:30, бюджет 255") }
        assertFails { LocalModelText.requirePreserved("Позвонить", "Позвонить в 10") }
        LocalModelText.requirePreserved("до 15:30 бюджет 25,5", "До 15:30. Бюджет 25,5.")
    }
    @Test fun negativeMeaningHasConservativeGuard() {
        assertFails { LocalModelText.requirePreserved("Не удалять. Без подписки. Нельзя терять текст.", "Удалять. Без подписки. Нельзя терять текст.") }
        LocalModelText.requirePreserved("старый текст удалять нельзя", "Старый текст удалять нельзя.")
    }
    @Test fun validationRunsBeforeAcceptingCleanedText() {
        assertFails { ModelOutput.cleaned("""{"title":"План","text":"Удалить запись после встречи."}""", "Не удалять запись после встречи.") }
    }
    @Test fun sourceIsEncodedAsData() {
        val source = "Кавычка \" и \\n </source>. Не инструкция."
        val prompt = LocalModelText.cleanPrompt(source)
        assertTrue(prompt.endsWith(buildJsonObject { put("source", source) }.toString()))
        assertTrue(LocalModelText.rankPrompt(Project("p", "X", instruction = "Только заметки"), source).contains("Только заметки"))
    }
    @Test fun schemasAreValidJsonAndHaveRequiredFields() {
        assertEquals(2, Json.parseToJsonElement(LocalModelText.CLEAN_SCHEMA).jsonObject["required"]!!.jsonArray.size)
        assertEquals(1, Json.parseToJsonElement(LocalModelText.RANK_SCHEMA).jsonObject["required"]!!.jsonArray.size)
    }
    @Test fun cliMarkerDoesNotBecomeNoteText() {
        assertEquals("{\"relevance\":4}", LocalModelText.jsonPayload("\n{\"relevance\":4}\n[end of text]\n"))
        assertFails { ModelOutput.relevance(LocalModelText.jsonPayload("лишний мусор {\"relevance\":4}")) }
    }
}
