# ADR 001: MobileActions Integration & Tool-Calling Architecture

## Status
Accepted (Draft)

## Context
The Prioritize Android application includes a local, on-device AI assistant ("Your Second Brain"). Currently, task extraction from chat, repeating tasks, and offline memory matches are handled via custom regex string injection patterns (e.g., `###TASK_SUGGESTION###`). 

We want to utilize the fine-tuned `MobileActions 270M` model (optimized for on-device function calling) and Google Tensor G5 NPU hardware acceleration to support system-level actions (e.g. calendar events, map location, flashlight, wifi settings) and native task management.

## Decision
We will implement **Option B**: Integrate the tool-calling framework directly into the existing **Brain Screen** chat. 
* The system will dynamically register toolsets when a model supporting tool use is active.
* We will introduce a `supportsTools: Boolean` capability flag to `EdgeModelSpec` in `ModelRegistry.kt`. Tools will only be registered in `ConversationConfig` when this flag is `true`.
* For models where `supportsTools` is `false`, the app will fall back to a standard text-based conversation and the legacy context/parsing prompts.
* Native `@Tool` functions will replace string-parsing workarounds for task, repeating task, and special date suggestions.
* We will implement a **Callback-Action Pattern** (Option A) for tool execution. The `PrioritizeTools` class (implementing `ToolSet`) will take a callback lambda `onActionCalled: (Action) -> Unit`. When a tool is triggered, it will publish a structured action object. The `TaskViewModel` will consume this action and execute it safely using its database repositories and thread-appropriate dispatchers.
* We will expose a **Fully Agentic Tool Suite** (Option B) comprising:
    1.  *System Actions:* Flashlight toggle, open WiFi settings, display location on Map, send email, and create Google Calendar event.
    2.  *Prioritize Write Actions:* `createTask`, `createRepeatingTask`, and `createSpecialDate`.
    3.  *Prioritize Read Actions:* `getUpcomingTasks(limit)` and `searchMemories(query)`.
* We will employ an **Automated NPU Crash Guard & Fallback Strategy** (Option B):
    1.  We target Google Tensor G5 EdgeTPU hardware acceleration (`Backend.NPU`) on supported Pixel devices.
    2.  If the engine initializes with `Backend.NPU`, the `ConversationConfig` is built with `samplerConfig = null` to avoid native library incompatibility crashes.
    3.  If the NPU delegate crashes the process (SIGABRT), our pre-emptive `SharedPreferences`-based crash guard will catch it on the next launch and skip NPU initialization, falling back to GPU or CPU. If running on GPU or CPU, the conversation will be initialized with a standard `SamplerConfig` (topK, topP, temperature).
* We will implement a **Unified Model Loading Lifecycle** (Option 2):
    1.  The model loading lifecycle remains user-controlled via Settings. The app will not dynamically load and unload different models when switching between screens (such as Chat and Scratch Pad), avoiding 2–4 second loading lag spikes.
    2.  If the user's active selected model supports tools (`supportsTools == true`), the toolset is registered. If the selected model is text-only, tools are dynamically disabled.

## Consequences
* **Improved UX:** A unified chat interface that can execute system actions (flashlight, calendar events, map directions) and app-level actions (create task, retrieve list of tasks) seamlessly.
* **Architecture Cleanliness:** Elimination of raw string parsing (`###TASK_SUGGESTION###`) in favor of structured LiteRT-LM function calling.
* **Resource Optimization:** We must design toolsets to fail gracefully if the active model does not support tool use or if hardware-accelerated NPU constraints require different sampler configurations.
* **Safety & Stability:** Text-only models (like DeepSeek R1) are protected from receiving structured tool schemas, preventing potential parsing errors or hallucinations. Native SIGABRT crash guard flags guarantee app startup stability even if hardware acceleration library paths fail.
* **Separation of Concerns & Testability:** Keeping the `PrioritizeTools` class decoupled from Android `Context` and databases ensures it can be tested in isolation with plain unit tests.
* **Loading Latency Optimization:** Avoiding dynamic swapping between tabs ensures tab navigation is instant and lag-free, leaving memory constraints and model choice clearly managed by the user.





