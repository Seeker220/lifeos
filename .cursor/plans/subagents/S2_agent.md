---
title: "S2 — :agent, Azure Foundry LLM and goal expansion"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: "AZURE_LLM_ENDPOINT, AZURE_LLM_DEPLOYMENT, AZURE_LLM_API_KEY, AZURE_LLM_API_VERSION (all optional)"
timebox: "85 minutes"
---

# S2 — `:agent`

> You own the one thing that makes LifeOS look like magic: a single sentence becoming a dozen enforced changes. You are also the single most likely component to fail live, so **the offline path is not a fallback, it is a first-class feature**. Build it first.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Turn user text into `List<Action>` and a persona reply. Call Azure AI Foundry when configured, fall back to deterministic canned expansions when not, and never let a malformed model response lose a turn.

## AI credentials

**Yes — and they are optional by design.**

- `AZURE_LLM_ENDPOINT`, `AZURE_LLM_DEPLOYMENT`, `AZURE_LLM_API_KEY`, `AZURE_LLM_API_VERSION`
- They arrive as an `LlmConfig` from `:core`, populated by `:app` from `local.properties` via BuildConfig. **You never read `BuildConfig` yourself** — `:agent` is a pure JVM module and has none.
- The exact model is undecided. Write for "some GPT deployment on Azure Foundry" and make the request body degrade gracefully, per Step 2.
- **Hard requirement:** with `LlmConfig.usable == false`, the app must still demo end to end. Build and test that path before touching HTTP.

---

## Files you own

- `agent/src/main/kotlin/com/lifeos/agent/**`
- `agent/src/test/kotlin/com/lifeos/agent/**`

## Files you must NOT touch

`core/**` (frozen), `domain/**`, `enforce/**`, `email/**`, `ui/**`, `app/**`, any `build.gradle.kts`. If you need a dependency beyond OkHttp, you cannot have it — adding one triggers a download that stalls seven other sessions.

---

## Contracts you implement

```kotlin
class AzureFoundryClient(private val config: LlmConfig) : LlmClient {
    override suspend fun complete(req: LlmRequest): Result<String>   // raw assistant content
}

class AgentController(
    private val chat: ChatStore,
    private val lifeState: LifeStateStore,
    private val executor: ActionExecutorPort,
    private val projection: ProjectionPort,
    private val compactor: CompactorPort,
    private val llm: LlmClient?,          // null when unconfigured
) : AgentPort {
    override suspend fun send(userText: String): AgentTurnResult
}

object SystemPromptBuilder { fun build(persona: Persona, projection: LifeStateProjection, chatSummary: String): String }
object ActionParser { fun parse(raw: String): ParsedTurn }          // ParsedTurn(reply, actions, skipped)
object OfflineFallbacks { fun match(userText: String, state: CanonicalLifeState): ParsedTurn }
```

`ParsedTurn` is yours, declared in `:agent` — do not add it to the frozen `:core` API.

---

## Step 1 — `OfflineFallbacks` first (25 minutes)

Build this before the HTTP client. It makes the app demoable immediately, it gives you golden expected output to test the real model against, and it is the thing that saves the pitch when the venue wifi dies.

Keyword-scored matching, case-insensitive, highest score wins, with a generic catch-all so there is never a dead end.

### Fallback 1 — the headline demo: "crack a Google interview in 1 month"

Triggers on any two of: `interview`, `google`, `crack`, `month`, `faang`, `dsa`, `leetcode`.

Must return a **full expansion**, not just a goal. This single response is what proves the product thesis, so it is the most important literal in the codebase:

```json
{
  "reply": "One month. I've put interview grind on your calendar 7 to 9 on weekdays, LeetCode daily, and a mock every Saturday. Instagram is capped at 30 minutes a day and YouTube at 45. Don't argue with me on day three.",
  "actions": [
    {"type":"create_goal","id":"g_google","title":"Crack Google interview","deadlineIso":"<today+30d>","hardness":"HARD"},
    {"type":"create_habit","title":"LeetCode daily","daysOfWeek":[1,2,3,4,5,6,7],"timeHhmm":"19:00","sourceGoalId":"g_google"},
    {"type":"create_habit","title":"Mock interview","daysOfWeek":[6],"timeHhmm":"10:00","sourceGoalId":"g_google"},
    {"type":"add_schedule_block","title":"Interview grind","startHhmm":"19:00","endHhmm":"21:00","kind":"STUDY","daysOfWeek":[1,2,3,4,5],"sourceGoalId":"g_google"},
    {"type":"create_task","title":"Graphs and trees set","dueIso":"<today+3d>","estMinutes":120,"sourceGoalId":"g_google"},
    {"type":"create_task","title":"System design notes: caching, sharding","dueIso":"<today+7d>","estMinutes":90,"sourceGoalId":"g_google"},
    {"type":"set_app_timeout","packageName":"com.instagram.android","limitMinutes":30,"sourceGoalId":"g_google"},
    {"type":"set_app_timeout","packageName":"com.google.android.youtube","limitMinutes":45,"sourceGoalId":"g_google"},
    {"type":"set_focus_windows","windows":[{"daysOfWeek":[1,2,3,4,5],"startHhmm":"19:00","endHhmm":"21:00","mode":"BLACKLIST","packages":["com.instagram.android","com.google.android.youtube"]}],"sourceGoalId":"g_google"},
    {"type":"set_alarm","label":"bedtime-check","timeHhmm":"22:30","personaLine":"LeetCode done? Don't lie to me.","sourceGoalId":"g_google"},
    {"type":"remember","fact":"Google interview in one month; strict on social app timeouts"}
  ]
}
```

Compute `<today+Nd>` at call time with `Time.plusDaysIso` — never hardcode a date, or the demo silently rots.

### Fallback 2 — whitelist focus

Triggers on `focus` plus any of `whitelist`, `only`, `just`. Emit `focus_set_apps` (WHITELIST with Chrome, Docs, LifeOS), `network_set_mode` WHITELIST, `network_set_apps` with the same list, `focus_start` for 50 minutes, and a `remember`.

### Fallback 3 — deadline plus doomscroll plus wake-up

Triggers on any two of `assignment`, `due`, `tuesday`, `instagram`, `doomscroll`, `wake`. Emit `create_goal` (hard, due in 2 days), one `create_task`, `set_alarm` at `07:00`, `set_app_timeout` Instagram 20 minutes, `focus_set_apps` BLACKLIST Instagram, and a `remember`.

### Fallback 4 — email check

Triggers on `email`, `inbox`, `mail`, `exam`. Reply pointing at the Inbox tab, zero actions. Cheap, and it keeps the demo's email beat working even if `:email` gets cut.

### Fallback 5 — generic catch-all

Always matches with score zero. Create a single `create_task` from the utterance (truncated to 80 chars) and reply in persona: `"Noted. It's on your list."` A demo that answers everything badly beats a demo that answers nothing.

Every fallback returns `ParsedTurn` with `source = TurnSource.OFFLINE_FALLBACK`.

---

## Step 2 — `AzureFoundryClient` (25 minutes)

OkHttp only. No Retrofit, no serialization codegen for the wire types — hand-build the request JSON with `JsonObject` and read the response with `Json.parseToJsonElement`.

### URL construction

Handle both Azure endpoint shapes, because which one you get depends on how the Foundry resource was provisioned:

```
if endpoint contains "/openai/v1"      -> "<endpoint>/chat/completions?api-version=<apiVersion>"
else                                   -> "<trimmed endpoint>/openai/deployments/<deployment>/chat/completions?api-version=<apiVersion>"
```

Trim trailing slashes. Send the key as the `api-key` header (Azure's own scheme), and also set `Authorization: Bearer <key>` — harmless when unused and it covers the v1-style endpoints.

### Request body

```json
{
  "messages": [
    {"role": "system", "content": "<systemPrompt>"},
    {"role": "user",   "content": "<userPrompt>"}
  ],
  "response_format": {"type": "json_object"},
  "temperature": 0.4,
  "max_tokens": 1400
}
```

`{"type":"json_object"}` is deliberate. Strict `json_schema` requires `additionalProperties: false` with every property required, which cannot express our 25-variant action union without an enormous generated schema. The grammar lives in the system prompt instead, and `ActionParser` is tolerant. Revisit strict schemas after the hackathon.

### Parameter degradation — the model is undecided, so plan for rejection

Newer reasoning deployments reject `max_tokens` (wanting `max_completion_tokens`) and reject non-default `temperature`. Implement one retry ladder, logging each downgrade at `LifeOS/Agent`:

1. Full body as above.
2. On HTTP 400 whose body mentions `max_tokens` → swap to `max_completion_tokens`.
3. On HTTP 400 mentioning `temperature` → remove `temperature`.
4. On HTTP 400 mentioning `response_format` → remove it entirely and rely on the prompt (the parser already strips markdown fences).
5. Any remaining failure → `Result.failure`, and `AgentController` goes offline.

Cap at three retries total. Do not loop.

### Timeouts and errors

- Connect 5s, read 12s, call 15s. A hung request is worse than a wrong answer during a live demo.
- 401 or 403 → fail fast with `"LLM auth rejected"`. Do not retry; the key is wrong and retrying burns stage time.
- 429 → one retry after 1.5s, then fail.
- Response extraction: `choices[0].message.content` as a string. Missing or blank → failure.
- **Never log `apiKey`, and never log the full response body at info level.** Log lengths and status codes.

Return `Result<String>` containing the raw assistant content. Parsing is `ActionParser`'s job, not the client's.

---

## Step 3 — `SystemPromptBuilder`

The prompt has five sections in this order. Keep the whole thing under roughly 3500 characters excluding the projection.

**1. Role.** LifeOS is an agent that *executes*, not a chatbot. It converts intent into scheduled, enforced device state.

**2. Persona voice.** Inject `persona.voice` verbatim and instruct that `reply` must be at most three sentences in that voice.

**3. Output contract.** Exactly one JSON object, no prose, no markdown fence:

```json
{"reply": "string", "actions": [ {"type": "...", "...": "..."} ]}
```

**4. Action grammar.** List all 25 `type` strings with their fields, one line each, terse. Include the enum literals (`SOFT|HARD`, `WHITELIST|BLACKLIST`, `OFF|BLACKLIST|WHITELIST`, `STUDY|GYM|DEEP_WORK|OTHER`) and state that `daysOfWeek` is ISO numbering with 1 = Monday. State that dates are `yyyy-MM-dd` or `yyyy-MM-ddTHH:mm` and that **all dates must be computed from the `today` field in the state projection**, never guessed.

**5. The expansion playbook.** This is the section that makes the product work, so make it emphatic:

> When the user commits to a goal, a single `create_goal` is a **failure**. You must also emit the concrete machinery that makes it happen, choosing from: milestone `create_task`s, recurring `create_habit`s, `add_schedule_block`s for study or gym windows, `set_app_timeout` daily caps on distracting apps, `set_focus_windows` covering the study blocks, `set_alarm`s for wake-ups and bedtime checks, and one `remember` fact.
>
> Scale the enforcement to the stakes. A one-month interview goal warrants a 30-minute Instagram cap. "Read more books" does not warrant any cap. Never cap an app the user needs for the goal itself.
>
> Assign the same `sourceGoalId` to every entity you create for that goal, using the `id` you gave the goal, so the user can undo the whole expansion in one tap.

Then two worked examples: one full expansion (reuse the Google-interview JSON from Step 1 so offline and online output are shaped identically) and one trivial turn (`"mark graph practice done"` → a single `complete_task`, showing that not every turn expands).

Finally append the live context:

```
CURRENT STATE (authoritative — never contradict this):
<projection.json>

EARLIER CONVERSATION SUMMARY:
<chatSummary or "none">
```

The projection is rebuilt every turn by `:domain`, which is why compaction cannot make the agent forget a goal.

---

## Step 4 — `ActionParser`

Tolerance is the whole point. The model **will** return fenced JSON, trailing prose, unknown action types, and wrong-typed fields. None of that may lose a turn.

1. Strip a leading ```` ```json ```` / ```` ``` ```` fence and any trailing fence.
2. Trim to the outermost balanced `{ ... }` if there is leading or trailing prose.
3. `Json.parseToJsonElement`. On failure, return `ParsedTurn(reply = raw.take(300), actions = emptyList(), skipped = [("parse", message)])` — showing the model's prose beats showing an error.
4. `reply` = string content, default `""`.
5. `actions` = `JsonArray`, default empty. **Iterate manually** — do not use polymorphic `kotlinx.serialization` deserialization, because one unknown `type` would throw and discard the whole array.
6. For each element: read `type` as a string, `when` over the 25 known values, read fields with defaults and lenient coercion (accept a number where a string is expected and vice versa; accept a single string where a list is expected). Unknown or unhandled → `SkippedAction(type, "unknown action type")`.
7. Enum fields: case-insensitive match, fall back to the default and record nothing (a wrong-cased enum is not worth a visible skip).

Write a small private helper set — `JsonObject.str(key, default)`, `.int(key, default)`, `.bool`, `.strList` — and use it everywhere. That helper is where all the leniency lives.

---

## Step 5 — `AgentController.send`

```
1. compactor.ensureWindow()
2. append the user ChatMessage to chat
3. state = lifeState.state.value
4. proj = projection.build(state); persona = Personas.byId(state.personaId)
5. system = SystemPromptBuilder.build(persona, proj, chat.transcript.value.summary)
6. parsed = if (llm != null && configUsable)
                llm.complete(LlmRequest(system, userText)).fold(
                    onSuccess = { ActionParser.parse(it) },
                    onFailure = { OfflineFallbacks.match(userText, state) })
            else OfflineFallbacks.match(userText, state)
7. report = executor.execute(parsed.actions, ActionOrigin.AGENT)
8. expansionGoalId = id of the create_goal in parsed.actions, if any
9. append the assistant ChatMessage with text = parsed.reply,
       appliedChips = report.applied.map { it.label }, expansionGoalId
10. return AgentTurnResult(parsed.reply, parsed.actions, report, source, expansionGoalId)
```

Details that matter:

- **Append the user message before calling the model.** The chat must feel responsive and the message must survive a crash mid-request.
- If `parsed.reply` is blank but actions applied, synthesise a reply from the chips: `"Done: " + chips.take(3).joinToString()`. Never render an empty bubble.
- If the LLM path fails, still return `TurnSource.OFFLINE_FALLBACK` rather than `ERROR`, and do not surface an error string to the user. The demo must look identical either way.
- Catch `Throwable` around the entire body. A crash here takes down the Chat tab, which is the home screen.
- Log one line per turn at `LifeOS/Agent`: source, action count, applied count, skipped count, elapsed ms. No prompt bodies, no keys.
- `expansionGoalId` is what lets S6 render the "Undo expansion" affordance, so populate it whenever a `create_goal` was applied.

---

## Verification

JVM tests, no emulator, no network.

```bash
cd /home/sumit/lifeos
./gradlew :agent:test
```

If Gradle reports `Timeout waiting to lock`, another session is building. Wait 20 seconds and retry.

Use fakes in `agent/src/test/kotlin/`: a `FakeLlmClient` returning canned strings, a `RecordingExecutor` capturing the action list, an in-memory `ChatStore` and `LifeStateStore`, and a `FixedProjection`.

Required tests:

- [ ] `OfflineFallbacks.match("I want to crack a Google interview in 1 month")` returns at least 10 actions, including two `set_app_timeout`s and at least one `add_schedule_block`
- [ ] Every action in that expansion shares one `sourceGoalId` matching the goal's `id`
- [ ] Deadlines in the expansion are computed relative to today, not hardcoded (assert the deadline is 30 days out from `Time.todayIso()`)
- [ ] `OfflineFallbacks.match("asdfgh")` returns exactly one `create_task` and a non-blank reply
- [ ] `ActionParser.parse` handles a ```` ```json ```` fence, leading prose, and a trailing sentence after the closing brace
- [ ] `ActionParser.parse` on an array containing one unknown `type` returns the known actions and one `SkippedAction`
- [ ] `ActionParser.parse` on syntactically invalid JSON returns a non-blank reply and zero actions, and does not throw
- [ ] `ActionParser.parse` coerces `"daysOfWeek": "1"` into `[1]` and `"limitMinutes": "30"` into `30`
- [ ] `AgentController.send` with `llm = null` still appends two chat messages and calls the executor once
- [ ] `AgentController.send` with a failing `FakeLlmClient` produces `TurnSource.OFFLINE_FALLBACK` and a non-blank reply
- [ ] `SystemPromptBuilder.build` output contains all 25 action type strings and the projection JSON
- [ ] No test and no log statement anywhere prints an API key

## Timebox

85 minutes, allocated: fallbacks 25, HTTP client 25, prompt 15, parser 15, controller 5.

If behind at 60 minutes, cut in this order: the parameter-degradation ladder (keep attempt 1 plus a plain failure), then fallbacks 3 and 4, then the extractive reply synthesis. **Never** cut: fallback 1, the tolerant parser, or the `llm == null` path. Those three are the demo.

## Handoff notes for S9

Report the exact `AppContainer` wiring:

```kotlin
val llmConfig = LlmConfig(BuildConfig.AZURE_LLM_ENDPOINT, BuildConfig.AZURE_LLM_DEPLOYMENT,
                          BuildConfig.AZURE_LLM_API_KEY, BuildConfig.AZURE_LLM_API_VERSION)
val llm = if (llmConfig.usable) AzureFoundryClient(llmConfig) else null
val agent = AgentController(chat, lifeState, executor, projection, compactor, llm)
```

Also state which Azure API version you defaulted to and whether the deployment name is expected in the URL path, so the user can fill `local.properties` correctly in one attempt.
