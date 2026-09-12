package brain.studio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class AiEnginesTest {
    @Test
    fun defaultsAreLocalAndRoleCompatible() {
        val selection = AiSelection().validated()
        AiRole.entries.forEach { role ->
            val engine = AiCatalog.engine(selection.engineId(role))!!
            assertTrue(engine.supports(role))
            assertEquals(AiLocality.LOCAL, engine.locality)
        }
    }

    @Test
    fun roleCannotUseWrongEngine() {
        assertFails {
            AiSelection(text = AiCatalog.DEFAULT_STT).validated()
        }
    }

    @Test
    fun externalPrivacyIsExplicitPerRole() {
        assertEquals(setOf(AiDataKind.AUDIO), AiPrivacy.dataFor(AiRole.SPEECH_TO_TEXT))
        assertEquals(setOf(AiDataKind.NOTE_TEXT), AiPrivacy.dataFor(AiRole.TEXT))
        assertEquals(
            setOf(AiDataKind.NOTE_TEXT, AiDataKind.PROJECT_TITLES, AiDataKind.PROJECT_INSTRUCTIONS),
            AiPrivacy.dataFor(AiRole.ROUTING),
        )
    }

    @Test
    fun providersCoverMainApis() {
        val ids = AiCatalog.cloudProviders.map { it.id }.toSet()
        assertTrue("openai" in ids)
        assertTrue("anthropic" in ids)
        assertTrue("gemini" in ids)
        assertTrue("openrouter" in ids)
        assertTrue("openai-compatible" in ids)
        assertTrue("custom" in ids)
    }
}
