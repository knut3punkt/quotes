package no.esotericgames.quotes.server.imagegen.comfyui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComfyUiEventTest {

    @Test
    fun `parses the message types the job uses`() {
        assertEquals(
            ComfyUiEvent.QueueStatus(2),
            ComfyUiEvent.parse("""{"type": "status", "data": {"status": {"exec_info": {"queue_remaining": 2}}, "sid": "x"}}"""),
        )
        assertEquals(
            ComfyUiEvent.ExecutionStart("p1"),
            ComfyUiEvent.parse("""{"type": "execution_start", "data": {"prompt_id": "p1", "timestamp": 1}}"""),
        )
        assertEquals(
            ComfyUiEvent.Progress("p1", 7, 40),
            ComfyUiEvent.parse("""{"type": "progress", "data": {"value": 7, "max": 40, "prompt_id": "p1", "node": "459:458"}}"""),
        )
        assertEquals(
            ComfyUiEvent.Executing("p1", null),
            ComfyUiEvent.parse("""{"type": "executing", "data": {"node": null, "prompt_id": "p1"}}"""),
        )
        assertEquals(
            ComfyUiEvent.ExecutionError("p1", "KSampler: out of memory"),
            ComfyUiEvent.parse(
                """{"type": "execution_error", "data": {"prompt_id": "p1", "node_type": "KSampler", "exception_message": " out of memory "}}""",
            ),
        )
        assertEquals(
            ComfyUiEvent.ExecutionInterrupted("p1"),
            ComfyUiEvent.parse("""{"type": "execution_interrupted", "data": {"prompt_id": "p1"}}"""),
        )
    }

    @Test
    fun `ignores unknown types and malformed messages`() {
        assertNull(ComfyUiEvent.parse("""{"type": "progress_state", "data": {"prompt_id": "p1", "nodes": {}}}"""))
        assertNull(ComfyUiEvent.parse("""{"type": "progress", "data": {"prompt_id": "p1"}}"""))
        assertNull(ComfyUiEvent.parse("""{"type": "execution_start", "data": {}}"""))
        assertNull(ComfyUiEvent.parse("not json"))
        assertNull(ComfyUiEvent.parse("[1, 2]"))
    }
}
