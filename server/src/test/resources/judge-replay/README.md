# Judge replay: model vs prompt (2026-10-11)

130 stored extraction candidates, exported from `quote_excerpts` and stratified:

- 30 accepted;
- 10 accepted with a dangling opener ("It", "This", "But"...), which is every such accept in the database;
- 30 rejected on fidelity;
- 30 rejected on quotability or completeness;
- 30 rejected on an unresolved reference.

Each set was replayed through the extraction judge by two models:

- **Ministral 3 8B** (Q4_K_M, local llama-server), using `JudgeReplayRunner llm`;
- **Claude Opus 5.5**, using sub-agents that follow the same prompt files.

Both models ran the production prompts (judge v3) and the new ones (judge v4).

| File | What |
|---|---|
| `sample.jsonl` | The candidates, with source text, context signals and the stored production verdict |
| `ministral-v3.jsonl`, `ministral-v4.jsonl` | Ministral replays |
| `claude-opus-v3.jsonl`, `claude-opus-v4.jsonl` | Claude replays. The v3 file has 129 rows (one item was skipped) |
| `report.md` | Output of `JudgeReplayRunner report`: per-stratum accepts, level distributions, every disagreement |

Regenerate the report with:

```
.\gradlew.bat :server:runJudgeReplay --args="report <dir>\sample.jsonl <dir>\report.md ministral-v3-replay=<dir>\ministral-v3.jsonl:v3 claude-v3=<dir>\claude-opus-v3.jsonl:v3 ministral-v4=<dir>\ministral-v4.jsonl:v4 claude-v4=<dir>\claude-opus-v4.jsonl:v4"
```

## Findings

**Noise floor.** The same model with the same prompt, run twice (the stored production verdicts against a Ministral v3
replay), agrees on 88% of accept/reject decisions.

| Change | Decisions that change |
|---|---|
| Same prompt (v3), Ministral to Claude | 31% |
| Same prompt (v4), Ministral to Claude | 30% |
| Same model (Claude), v3 to v4 prompts | 8% |
| Same model (Ministral), v3 to v4 prompts | 30% |

The table below scores each run against Claude v4. Claude v4 is a reference, not ground truth; hand labels would be
better.

| Run | True accept | False accept | Missed | True reject | Agreement |
|---|---|---|---|---|---|
| Stored Ministral v3 (production) | 19 | 21 | 24 | 66 | 65% |
| Ministral v4 | 32 | 28 | 11 | 59 | 70% |
| Claude v3 | 33 | 0 | 10 | 86 | 92% |

**Fidelity calls.** Ministral and Claude agree on fidelity of 4 or more for 66% of candidates with v3, and 80% with
v4. Fidelity v3 compares the excerpt itself with the source, and says outright that leaving things out is fine. That
fixed most of Ministral's "the excerpt omits X" rejections. Under the old prompts, Claude rated 29 of the 30
production fidelity rejections at fidelity 4 or more.

**Quotability calls.** The models agree on quotability of 4 or more for only 58% of candidates with v3, and 64% with
v4. Ministral's quotability stays close to binary under both prompts: about 60 of 130 at level 5, against 2 or 3 for
Claude. The prompt did not fix this; it is a limitation of the model.

**Unresolved references.** Ministral over-lists ordinary nouns as unresolved references. Even so, most candidates
rejected that way are flat, and Claude rejects them too (1 of 29 accepted). The over-listing therefore cost few good
excerpts in this sample.

## Conclusion

In the judge, the prompt explains a minority of the errors.

- Claude barely needed v4: only 8% of its decisions changed.
- With either prompt, about 30% of Ministral's decisions differ from Claude's.
- With Ministral, v4 recovers most of the good excerpts it used to reject (missed drops from 24 to 11). It also lets
  more weak ones through (false accepts rise from 21 to 28).
