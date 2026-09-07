# AGENTS.md

## Project

CycleLens is an Android companion tool for tracking Clash Royale card cycle state.

Current architecture:

```text
cycle-core
    ↓
MatchSession
   ↙      ↘
Activity  OverlayService
              +
        CaptureService
```

Major responsibilities:

```text
cycle-core
- Pure Kotlin/JVM
- Card cycle rules
- Observation history
- Undo/reset
- No Android dependencies

app/catalog
- Canonical card metadata
- Official card artwork
- Visual forms
- Evolution/Hero canonicalization
- Tower Troop exclusion

MatchSession
- Application-scoped match state
- Sole runtime owner of CycleTracker

OverlayService
- TYPE_APPLICATION_OVERLAY
- Manual card-cycle interaction
- specialUse foreground service

CaptureService
- MediaProjection
- VirtualDisplay
- ImageReader
- Capture profiles
- Sampling
- Frame acquisition
- mediaProjection foreground service
```

The project currently has a validated Samsung Android 16 capture baseline.

Recommended capture baseline:

```text
Profile: BALANCED
Capture output: approximately 720×1560
Analysis rate: 15 FPS
Arena normalized rect:
left   = 0.000
top    = 0.095
right  = 1.000
bottom = 0.855
```

Consult `docs/validation.md` before changing capture behavior, performance assumptions, or Samsung-specific lifecycle handling.

---

## Engineering priorities

Prioritize, in order:

1. Correctness.
2. Explicit ownership and lifecycle.
3. Bounded memory and latency.
4. Stable Android lifecycle behavior.
5. Measurable performance.
6. Small coherent changes.
7. UI polish.

Do not trade ownership correctness for micro-optimizations.

Do not introduce abstractions merely because they may become useful later.

Prefer the smallest architecture that makes ownership, lifetime, threading, and testing clear.

---

## Main-agent responsibilities

The primary agent owns decisions involving:

- architecture
- module boundaries
- ownership and lifetime
- concurrency
- backpressure
- buffer management
- Android Service lifecycle
- MediaProjection lifecycle
- public APIs
- cross-module refactors
- performance trade-offs
- security/privacy boundaries
- final diff review

These decisions must not be delegated wholesale to a subagent.

Subagents may investigate these areas, but the primary agent must synthesize the findings and make the final design decision.

Before changing a critical ownership or concurrency path, the primary agent should understand the complete relevant lifecycle.

---

## Default subagent strategy

For non-trivial tasks, prefer reconnaissance before modification.

A typical workflow is:

```text
Primary agent
    ↓
delegate focused investigation
    ↓
collect concise findings
    ↓
primary agent decides design
    ↓
implement
    ↓
delegate targeted validation where useful
    ↓
primary agent reviews final diff
```

Do not create subagents for trivial edits that can be completed faster directly.

For substantial tasks, normally use 2–4 focused subagents rather than one vague large subtask.

Avoid assigning multiple editing agents to the same files unless worktree isolation and merge ownership are explicit.

---

## Good tasks for subagents

Prefer delegating work that is context-heavy but reasoning-light or independently verifiable.

### Repository reconnaissance

Examples:

```text
Find every owner and user of ImageReader.
Trace CaptureService startup and shutdown.
Find all code paths that release Surface objects.
Find every use of AnalysisFrameDescriptor.
Map MatchSession callers.
Find all tests covering resize.
```

Subagents should return relevant files and concise findings rather than copying large source files.

### Code reading

Examples:

```text
Summarize the CaptureService resource lifecycle.
Explain the current resize sequence.
Describe how SamplingGate interacts with ImageReader.
Map the Activity → CaptureService consent flow.
```

### Test investigation

Examples:

```text
Find existing tests relevant to buffer ownership.
Identify missing edge cases.
Run the capture unit tests and summarize failures.
Check whether a change breaks existing lifecycle tests.
```

### Static inspection

Examples:

```text
Look for leaked Image/Surface/ByteBuffer ownership.
Look for unbounded collections or queues.
Look for allocations inside frame callbacks.
Look for Android objects escaping their intended lifetime.
Look for StateFlow updates occurring per frame.
```

### Mechanical changes

Examples:

```text
Rename a type across the project.
Move clearly isolated helpers.
Add repetitive test cases.
Update documentation after the design is already decided.
```

### Validation

Examples:

```text
Run Gradle tests.
Run lint.
Run git diff --check.
Inspect log output.
Review the final diff for unrelated changes.
```

---

## Tasks that should remain with the primary agent

Do not delegate final responsibility for:

```text
pixel-buffer ownership model
buffer-pool design
queue/backpressure policy
Image lifetime
Surface lifetime
resize coordination
CaptureService shutdown ordering
threading model
public FrameAnalyzer API
cross-module architecture
performance budget decisions
automatic-observation semantics
final diff review
```

A subagent may research or critique these decisions.

The primary agent makes and validates them.

---

## Subagent task format

Give every subagent a narrow question and explicit output contract.

Preferred form:

```text
Task:
Inspect the ImageReader → FrameSamplingGate path.

Do not modify files.

Return only:
- relevant files and symbols
- current ownership/lifetime
- allocations in the callback
- possible blocking points
- existing tests
- concrete risks
- recommendation, if any
```

For an editing subagent:

```text
Task:
Add focused tests for FrameBufferPool exhaustion.

Allowed files:
- <specific test files>

Do not change production code.

Return:
- files changed
- scenarios added
- test command
- result
```

Do not tell a subagent simply:

```text
Investigate Stage 6D.
```

Break the work into bounded questions.

---

## Subagent output hygiene

Subagents should keep noisy intermediate work out of the primary thread.

Return summaries rather than raw logs.

For commands/tests, report:

```text
command
exit status
test count where available
failing test names
relevant error excerpt
likely cause
```

Do not paste hundreds of successful Gradle lines unless needed.

When useful, save verbose temporary logs outside the repository and summarize them.

Never commit generated logs.

---

## Current capture invariants

Preserve these unless the task explicitly changes them.

### MediaProjection

One user consent corresponds to one capture session.

Do not cache or persist MediaProjection authorization data for reuse.

A new capture session requires new user consent.

CaptureService owns:

```text
MediaProjection
VirtualDisplay
ImageReader
Surface
capture callback
capture worker resources
```

Activity must not own these resources.

### ImageReader

Current format:

```text
RGBA_8888
maxImages = 2
```

Use:

```text
acquireLatestImage()
```

Every acquired Image must be closed.

An Android `Image` must not escape the ImageReader callback lifetime unless an explicit new ownership design is approved.

Never queue Android `Image` objects for asynchronous processing.

### Capture sampling

Incoming MediaProjection frames may arrive near display refresh rate.

Analysis sampling is separate from producer frame rate.

Do not use sleeping or blocking to limit frame rate.

Dropped frames are acceptable.

Backlog is not.

### Capture geometry

Use:

```text
CaptureGeometry
NormalizedRect
PixelRect
```

for coordinate mapping.

Do not scatter device-specific pixel coordinates through the codebase.

Do not hard-code Samsung 1440×3120 coordinates into analysis algorithms.

### Arena

Current calibrated normalized arena region:

```text
left   = 0.000
top    = 0.095
right  = 1.000
bottom = 0.855
```

Treat it as capture-layout data, not as arbitrary magic numbers.

---

## Frame-pipeline rules

When implementing Stage 6D or later frame processing:

- Capture callback must remain short.
- Never perform expensive CV directly inside ImageReader callbacks.
- Never wait for an analysis consumer.
- Never allow an unbounded frame queue.
- Prefer latest useful data over processing stale frames.
- Dropping frames is preferable to increasing latency.
- Avoid per-frame multi-megabyte allocations.
- Reuse pixel buffers when practical.
- Every buffer must have a clear owner at all times.
- Every ownership transfer must have a corresponding release path.
- Exceptions, cancellation, resize, and Service shutdown must release owned resources.
- Do not expose mutable buffers through StateFlow.
- Do not publish UI state at frame rate.

Any new asynchronous pixel pipeline must document:

```text
producer
owner
transfer point
queue capacity
overflow policy
consumer
release point
shutdown behavior
resize behavior
```

before or alongside implementation.

---

## Performance budget

Current Samsung SM-S9260 reference:

```text
BALANCED capture only:
approximately 15% app CPU

NATIVE:
approximately 51% app CPU

ECO:
approximately 12.8% app CPU
```

Treat these as measured baselines, not test assertions.

For future analysis work, prefer incremental measurement.

Approximate design target:

```text
Capture                    ~15%
Frame copy / transport     low single digits
Event proposal             low single digits
Expensive classification   event-driven, not every frame
```

If an otherwise empty analysis pipeline pushes sustained app CPU toward 25–30%, investigate before adding more CV work.

Do not optimize by lowering Clash Royale's frame rate or system refresh rate.

---

## Card-domain invariants

`cycle-core` only understands canonical `CardId`.

It must not depend on:

```text
Android
Bitmap
card artwork
visual-form artwork
MediaProjection
OpenCV
ML
Catalog JSON
```

Visual forms normalize before reaching cycle logic.

Examples:

```text
knight_normal
knight_evolution
hero_knight
        ↓
knight
```

Champion cards such as Archer Queen are independent canonical cards.

Tower Troops are not cycle eligible.

Do not send non-cycle-eligible catalog objects to MatchSession.

Do not duplicate canonicalization logic in detector code.

---

## Detection architecture direction

Future automatic recognition should conceptually follow:

```text
Captured pixels
    ↓
preprocessing
    ↓
event proposal
    ↓
visual classification
    ↓
VisualDetection
    ↓
canonicalization
    ↓
temporal deduplication / validation
    ↓
MatchSession.observe()
```

Do not collapse these stages into one opaque class.

In particular:

```text
detector result
```

must not immediately mean:

```text
confirmed card play
```

Detection confidence, temporal deduplication, and event confirmation must remain distinct concepts.

Do not start card detection unless the user task explicitly requests that stage.

---

## Android boundaries

Preserve the two foreground-service roles:

```text
OverlayService
→ specialUse
→ TYPE_APPLICATION_OVERLAY

CaptureService
→ mediaProjection
→ screen acquisition
```

Do not merge them merely to reduce class count.

Stopping one service must not implicitly stop the other unless a future explicit design requires it.

Do not add:

```text
AccessibilityService
root
hooks
game-process memory access
touch injection
automatic game interaction
```

CycleLens observes and presents information.

It does not control Clash Royale.

---

## Network and privacy

The runtime app intentionally does not require network access for its core functionality.

Do not add `INTERNET` permission unless the task explicitly requires and justifies it.

Card metadata and artwork are bundled locally.

Never commit:

```text
API tokens
credentials
private keys
local.properties
heap dumps
debug screenshots
screen captures
```

Supercell API credentials belong only in environment variables used by development-time tooling.

Do not log raw screen pixels.

Do not persist captured frames unless a debug-only, user-initiated task explicitly requires it.

Debug screenshots must remain temporary and private.

---

## Catalog

Runtime catalog:

```text
app/src/main/assets/cards.json
app/src/main/assets/card-icons/
```

Development-time updater:

```text
tools/update-card-catalog/
```

Preserve manual canonical mappings and visual-form semantics when syncing upstream metadata.

External API schemas must not become runtime domain models directly.

Official card artwork is UI/display data, not automatically a CV detection template.

---

## Testing expectations

Run focused tests while developing.

Before finalizing a non-trivial change, run:

```bash
./gradlew test
./gradlew :cycle-core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
git diff --check
```

If catalog updater code changes, also run its existing updater test suite.

If a task modifies Android runtime behavior and a Samsung device is connected, perform the relevant real-device smoke test when practical.

Never claim manual touch behavior was verified if only ADB/state inspection was performed.

Never claim pixel correctness without pixel-level evidence.

---

## Android debugging

Useful commands include:

```bash
adb devices

adb shell dumpsys activity services com.eureka.cyclelens
adb shell dumpsys media_projection
adb shell dumpsys meminfo com.eureka.cyclelens
```

Use logcat selectively.

Do not emit per-frame logs.

When investigating performance, distinguish:

```text
incoming FPS
accepted FPS
copied FPS
processed FPS
```

Do not call all of them simply "FPS".

---

## Git hygiene

Before modifying code:

```bash
git status --short
```

Do not overwrite unrelated user changes.

Keep patches scoped to the requested task.

Do not perform opportunistic refactors unrelated to the current goal.

Do not commit unless the user explicitly asks for a commit.

Never amend or rewrite user commits unless explicitly requested.

Before finishing:

```bash
git diff --check
git status --short
```

Report unrelated pre-existing changes separately.

Do not add generated APKs, heap dumps, debug screenshots, benchmark logs, or temporary files to Git.

---

## Documentation

Keep `AGENTS.md` concise and operational.

Detailed benchmark and device-validation results belong in:

```text
docs/validation.md
```

When architecture becomes complex enough to require durable explanation, prefer dedicated files under `docs/` rather than continually expanding this file.

Update documentation when a measured assumption or lifecycle contract materially changes.

---

## Final review checklist

Before presenting completion of a substantial task, the primary agent must review the final diff and answer:

```text
Did ownership become clearer or less clear?
Can any queue grow without bound?
Can any Image/Surface/buffer leak?
Can callbacks block?
Can stale frames accumulate?
Does resize release old resources?
Does stop release every owned resource?
Did Android lifecycle behavior change?
Did cycle-core gain an inappropriate dependency?
Did runtime networking appear?
Did scope expand beyond the requested stage?
Are tests validating behavior rather than implementation details?
Were measured performance claims actually measured?
```

Do not finish merely because tests pass.

Tests are necessary but do not replace lifecycle, ownership, and diff review.