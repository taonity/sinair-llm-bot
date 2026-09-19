# Bot Reply Design

## Reply ownership

Incoming human messages enter `pending_bot_message`. The debouncer processes small batches per
room; a five-second recovery scan resumes due work after cooldowns or a backend restart. Queuing
the final outbound message and removing its pending request share one transaction. Room processing
is serialized within a backend process; deployments should run one active bot worker. This is not
distributed exactly-once execution for external tool side effects.

Triage judges an identified target against the current transcript, including later messages.
An explicit leading `@` mention of the current bot nickname or an alias is handed straight to
generation without a gate-model call. Matching is case-insensitive with a nickname boundary;
quoted mentions, mentions elsewhere, and names that merely share a prefix still go through the gate.
Generation checks the actual recipient and may choose silence before using tools. For ambiguous
direct requests and active follow-ups, the gate prefers handing off rather than rejecting them;
unsolicited participation remains conservative.
Direct requests and active continuations use a two-second cooldown and a 40-reply/20-minute
window by default. Open questions and material unsolicited contributions wait 45 seconds, then
are reassessed. Existing ordinary limits (30 seconds, eight replies/20 minutes) still apply to
unsolicited contributions. A cooldown defers a request; it does not discard it. Random spontaneous
replies are removed. Messages received while asleep are not queued.

The bot yields when a person has answered adequately, is explicitly taking the question, or is
the named recipient. It does not build speculative expertise profiles. After a long generation,
new human messages trigger a final relevance check before delivery. Both triage and generation
can choose silence when the target is already resolved. A triage outage retains work without
posting an unsolicited error.
The final relevance check does not use the direct-mention shortcut: it asks specifically whether
later messages withdraw, replace or resolve the target, not whether participation was worthwhile.

## Generation and budgets

One strong model drafts the answer. Full tool schemas are loaded through `discover_tools` only
when needed. The session rejects unoffered tools. Repository writes require the write feature
toggle and a request from the configured developer account. Read-only retries are separate from
write execution, which is never automatically retried by the client. Identical repository reads
are reused within a generation; live application/log reads are not cached. Writes invalidate the
read cache. Fetched URL sources have a bounded five-minute cache; large image data is not cached.

Reasoning-capable provider turns preserve `reasoning_details` unchanged. Output exhaustion gets
one larger-budget attempt with tools still available, then tool-free finalization and a bounded
recovery attempt. Defaults under `app.llm.tool-loop`:

| Setting | Default |
| --- | --- |
| `max-rounds` | 20 successful tool rounds |
| `final-max-tokens` | 6000 |
| `recovery-max-tokens` | 12000 |
| `max-context-chars` | 120000 |
| `max-tool-result-chars` | 12000 |
| `max-duration-seconds` | 240 |
| `reasoning-effort` | low |

These are ceilings, not target consumption. Context limiting is a character-based safety budget,
not an exact model tokenizer. Old tool outputs become marked excerpts when it is exceeded, which
forces finalization. Retained reasoning and tool arguments count too. If reasoning history alone
cannot fit, finalization starts a fresh synthesis turn with the original request and bounded evidence
instead of modifying opaque reasoning blocks. Image inputs use a conservative size estimate rather
than their base64 transport length. The elapsed-time check happens between calls; it cannot interrupt an in-flight
HTTP request. Provider failures get three transport attempts by default, not twenty. Partial output
is marked internally and gets an answer-format repair attempt; exhausted provider failures still
produce the existing pipeline-linked failure notice. Reasoning text is never substituted for an answer.

The critic reviews substantive drafts (1200 characters or three tool calls by default), using
bounded tool evidence. It can request one tool-free repair. There are no parallel candidates or
second comparative critique. Formatting validation applies independently of the critic.

## Rendering and memory

The final-answer contract is `{"lead":"...","blocks":[{"kind":"prose|code|quote","text":"..."}]}`.
Short replies use only `lead`. The renderer owns fences and quote placement, and typed code blocks
preserve their content. Legacy prose/fences still render; an absent description gets a neutral
outside lead instead of moving an arbitrary code line. Malformed JSON is repaired when possible
and is never dumped into chat. The default final chat character cap is 8000; the prompt receives
the effective cap. An oversized fenced block is omitted with an explicit size-limit notice rather
than publishing partial executable code. The full draft remains in the pipeline trace.

Transcripts retain message IDs, times, line breaks, and both the beginning and end of long messages.
Triage and generation merge recent locally queued replies into the transcript before their chat
echo arrives. These replies have `trigger_message_id` provenance, a `bot=self` marker and an explicit
PENDING, CLAIMED or SENT state; sent here means collector acknowledgement, not confirmed visibility
to participants. Command outbounds, including private-message commands, have no reply provenance
and are excluded. The overlay is room-scoped, limited to the configured session-gap interval and
shares the transcript's message-count limit. It includes completed replies, never in-progress drafts.
Once ingestion links an echo through `source_outbound_message_id`, the actual chat message replaces
the local entry. A second ID-based check handles an echo arriving between the two context reads.
Linked bot messages remain recognizable as self-authored under older nicknames. Summaries continue
to use ingested chat messages only, rather than treating unconfirmed local output as delivered speech.
Summary refresh runs in the background and advances a received-time/message-ID watermark rather
than subtracting retained row counts. Retention cleanup cannot make that watermark move backwards.
Sources and prior summaries remain reference data, not authority or unfinished tasks.

## Rollout

Apply Flyway migrations V100018, V100019 and V100020 before starting the new backend. V100020 adds
outbound reply provenance for the context overlay; existing outbounds are not backfilled and still
become visible through normal echo ingestion. No frontend rebuild
is required for the dynamic config schema beyond normal deployment.

Saved console overrides and environment values still win over deployed defaults. Review/reset
`app.bot.persona.prompt`, `app.llm.critic.prompt`, tier token limits, tool-loop limits and retry settings
when adopting this behavior. In particular an old persona override can retain the human-imitation
instructions. Removed candidate-count, candidate-temperature and spontaneous-probability overrides
are ignored; their stored history is not deleted. No production settings are changed automatically.

## Evaluation

Automated tests use local scripted providers and stubs, not paid model calls. They verify recovery,
tool boundaries, rendering, request retention, cache limits, migrations and summary watermarks.
They do not establish real-model conversational quality. Replay representative room excerpts with
the effective production prompt before selecting new models or lowering budgets:

| Scenario | Expected behavior |
| --- | --- |
| Direct follow-up within 30 seconds | Answer or retain it; never silently drop it |
| Leading current bot nickname or alias | Hand off without a gate call; generation may suppress an inapplicable request |
| Next request arrives before the previous reply's echo | Include the queued reply once, labeled with its delivery state |
| Short acceptance of a pending action | Carry out the action, without asking permission again |
| Open question followed by a human answer | Stay silent after the grace period |
| Unanswered open question | Give a substantive answer after the grace period |
| Question addressed to another person | Yield unless explicitly invited |
| Opinion request after a detailed debate | Judgment plus a material insight, not a recap |
| Reasoning-only truncated provider turn | Retry with more output room and finish |
| New human answer during investigation | Suppress an obsolete contribution |
| Long code with blank lines | Outside lead, intact code and balanced fences |
| Command delivered without confirmation | Report uncertainty, not success; do not replay it |

Compare missed requests, unwanted interruptions, redundant replies, unsupported claims, completion
rate, latency and provider cost per completed task. Higher output ceilings do not themselves imply
higher spend, but selective critique and discovery can add calls; measure actual usage.