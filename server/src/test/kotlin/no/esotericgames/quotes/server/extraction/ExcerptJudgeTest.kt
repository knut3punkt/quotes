package no.esotericgames.quotes.server.extraction

import kotlinx.coroutines.runBlocking
import no.esotericgames.quotes.server.extraction.llm.ExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.FidelityVerdict
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.JudgeOutcome
import no.esotericgames.quotes.server.extraction.llm.JudgeRequest
import no.esotericgames.quotes.server.extraction.llm.StandaloneVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val POLICY = ExtractionPolicy()
private const val SOURCE_TEXT = "He waited for years. Patience teaches more than the thing we wait for."

private val CANDIDATE = ExcerptCandidate(
    startUnit = 2,
    endUnit = 2,
    startOffset = 21,
    endOffset = SOURCE_TEXT.length,
    text = "Patience teaches more than the thing we wait for.",
    wordCount = 9,
    reason = "Core idea: patience",
    contextSignals = emptyList(),
)

private fun standalone(
    unresolvedReferences: List<String> = emptyList(),
    insight: String = "Waiting itself teaches.",
    standsAlone: Int = 5,
    completeness: Int = 5,
    quotability: Int = 4,
) = StandaloneVerdict("Patience.", unresolvedReferences, insight, standsAlone, completeness, quotability)

private class FakeJudgeClient(
    private val standaloneOutcome: JudgeOutcome<StandaloneVerdict>,
    private val fidelityOutcome: JudgeOutcome<FidelityVerdict> = JudgeOutcome.Success(FidelityVerdict("Same.", 5, "faithful"), "judge"),
) : ExcerptJudgeClient {
    val standaloneRequests = mutableListOf<JudgeRequest>()
    val fidelityRequests = mutableListOf<JudgeRequest>()

    override suspend fun judgeStandalone(request: JudgeRequest): JudgeOutcome<StandaloneVerdict> {
        standaloneRequests += request
        return standaloneOutcome
    }

    override suspend fun judgeFidelity(request: JudgeRequest): JudgeOutcome<FidelityVerdict> {
        fidelityRequests += request
        return fidelityOutcome
    }
}

private fun judgeWith(client: ExcerptJudgeClient) = ExcerptJudge(client, GenerationSettings("judge", 0.1, 500), POLICY)

class ExcerptJudgeTest {

    @Test
    fun `levels map onto the 0-100 scale and clamp out-of-range values`() {
        assertEquals(listOf(0, 25, 50, 75, 100), (1..5).map { ExcerptJudge.levelToScore(it) })
        assertEquals(0, ExcerptJudge.levelToScore(0))
        assertEquals(100, ExcerptJudge.levelToScore(99))
    }

    @Test
    fun `a strong verdict on both calls meets the thresholds`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(), "judge"))

        val judged = assertIs<ExcerptJudgment.Judged>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertTrue(judged.excerpt.meetsThresholds)
        assertEquals(100, judged.excerpt.independence)
        assertEquals(75, judged.excerpt.quotability)
        assertEquals(100, judged.excerpt.contextualFidelity)
    }

    @Test
    fun `the blind call never sees the source passage`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(), "judge"))

        judgeWith(client).judge(SOURCE_TEXT, CANDIDATE)

        val blindContent = client.standaloneRequests.single().userContent
        assertTrue(CANDIDATE.text in blindContent)
        assertFalse("He waited for years" in blindContent)
        assertTrue(SOURCE_TEXT in client.fidelityRequests.single().userContent)
    }

    @Test
    fun `context signals are passed to the blind call as targeted questions`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(), "judge"))

        judgeWith(client).judge(SOURCE_TEXT, CANDIDATE.copy(contextSignals = listOf("This")))

        assertTrue("\"This\"" in client.standaloneRequests.single().userContent)
    }

    @Test
    fun `an unresolved reference fails the excerpt even with top levels, and skips the fidelity call`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(unresolvedReferences = listOf("this")), "judge"))

        val judged = assertIs<ExcerptJudgment.Judged>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertFalse(judged.excerpt.meetsThresholds)
        assertNull(judged.excerpt.contextualFidelity)
        assertTrue(client.fidelityRequests.isEmpty())
        assertTrue("Unresolved: this" in judged.excerpt.judgeNotes)
    }

    @Test
    fun `an empty insight fails the excerpt even with top levels`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(insight = " ", quotability = 5), "judge"))

        val judged = assertIs<ExcerptJudgment.Judged>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertFalse(judged.excerpt.meetsThresholds)
    }

    @Test
    fun `a flat excerpt at quotability level 3 fails`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.Success(standalone(quotability = 3), "judge"))

        val judged = assertIs<ExcerptJudgment.Judged>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertFalse(judged.excerpt.meetsThresholds)
        assertEquals(50, judged.excerpt.quotability)
    }

    @Test
    fun `low fidelity fails an otherwise strong excerpt`() = runBlocking<Unit> {
        val client = FakeJudgeClient(
            standaloneOutcome = JudgeOutcome.Success(standalone(), "judge"),
            fidelityOutcome = JudgeOutcome.Success(FidelityVerdict("The author rejects this.", 2, "sarcastic"), "judge"),
        )

        val judged = assertIs<ExcerptJudgment.Judged>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertFalse(judged.excerpt.meetsThresholds)
        assertEquals(25, judged.excerpt.contextualFidelity)
    }

    @Test
    fun `a connection failure on either call maps to Unreachable`() = runBlocking<Unit> {
        val blindDown = FakeJudgeClient(JudgeOutcome.ConnectionFailure("refused"))
        val fidelityDown = FakeJudgeClient(JudgeOutcome.Success(standalone(), "judge"), JudgeOutcome.ConnectionFailure("refused"))

        assertIs<ExcerptJudgment.Unreachable>(judgeWith(blindDown).judge(SOURCE_TEXT, CANDIDATE))
        assertIs<ExcerptJudgment.Unreachable>(judgeWith(fidelityDown).judge(SOURCE_TEXT, CANDIDATE))
    }

    @Test
    fun `a malformed response maps to Malformed`() = runBlocking<Unit> {
        val client = FakeJudgeClient(JudgeOutcome.MalformedResponse("bad json"))

        val result = assertIs<ExcerptJudgment.Malformed>(judgeWith(client).judge(SOURCE_TEXT, CANDIDATE))

        assertEquals("bad json", result.message)
    }
}
