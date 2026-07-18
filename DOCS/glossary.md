# Glossary: On-Device AI & Tool Calling

This document defines the key terms and concepts related to the integration of on-device large language models and function calling in the Prioritize app.

---

### Terminology

*   **MobileActions 270M (FunctionGemma)**
    A 270-million parameter edge model based on Gemma 3, fine-tuned by Google to translate natural language user queries into structured tool/function call parameters.

*   **LiteRT-LM**
    Google's open-source on-device inference runtime for Large Language Models (formerly TensorFlow Lite / MediaPipe LLM Inference API). It manages KV-cache, tokenization, and execution backends on mobile devices.

*   **Backend.NPU**
    The LiteRT-LM hardware accelerator target that maps execution onto the Neural Processing Unit / Tensor Processing Unit (TPU). On Tensor G5 (Pixel 10 Pro), this targets Google's 4th-generation EdgeTPU.

*   **ToolSet / `@Tool` / `@ToolParam`**
    The LiteRT-LM Kotlin API interface and annotations used to declare functions and arguments that the model is allowed to call. The runtime automatically parses these into the system prompt's function declaration tags.

*   **Automatic Tool Calling**
    An execution mode where the LiteRT-LM runtime automatically intercepts a model's request to execute a tool, runs the corresponding Kotlin method, injects the result back into the context, and resumes model generation.

*   **SIGABRT (Signal Abort)**
    An OS-level crash signal thrown by native libraries (like LiteRT). Since it bypasses Java/Kotlin JVM exception catch blocks, it kills the application process and requires a pre-emptive crash guard mechanism to mitigate (e.g. SharedPreferences crash flags).
