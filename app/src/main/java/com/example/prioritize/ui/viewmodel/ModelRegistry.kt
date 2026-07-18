package com.example.prioritize.ui.viewmodel

/**
 * Central registry for all supported on-device LLM model specifications.
 *
 * Extracting this from TaskViewModel.kt serves three goals:
 * 1. Single source of truth — BrainScreen, TaskViewModel, and Gemma4Parser all
 *    import from here rather than duplicating or importing from each other.
 * 2. Testable in isolation — ModelRegistry has no Android dependencies.
 * 3. Reduces TaskViewModel size (previously a 1,450+ line God class).
 *
 * To add a new model: append a new EdgeModelSpec to AVAILABLE_MODELS below.
 * No other files need to change as long as the existing fields are populated.
 */

/**
 * Describes a single on-device LLM model available for download and use.
 *
 * @param id              Stable unique identifier (never changes, safe to persist).
 * @param name            Human-readable display name shown in the UI.
 * @param sizeLabel       Approximate download size (for informational display only).
 * @param description     One-line capability summary shown in the model manager.
 * @param filename        Local filename used to store and locate the model on disk.
 * @param downloadUrl     Direct URL to the .litertlm artifact on Hugging Face.
 * @param recommendedRamGb Minimum RAM (GB) for reliable inference without OOM.
 */
data class EdgeModelSpec(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val description: String,
    val filename: String,
    val downloadUrl: String,
    val recommendedRamGb: Double
)

/**
 * The model used when no preference has been saved yet.
 *
 * Intentionally the generic (non-NPU) E2B build so that new installs work
 * out-of-the-box on any device with GPU or CPU fallback.
 * The Tensor G5 NPU model remains selectable but requires LiteRT ≥ 0.14.x
 * to function without a native crash (known issue #2566).
 */
const val DEFAULT_MODEL_ID = "gemma_4_e2b_tensor_g5"

/** All models available in the Prioritize app's model manager. */
val AVAILABLE_MODELS: List<EdgeModelSpec> = listOf(
    EdgeModelSpec(
        id = "gemma_4_e2b_tensor_g5",
        name = "Gemma 4 E2B (Tensor G5 NPU)",
        sizeLabel = "3.7 GB",
        description = "Recommended for Pixel 10 Pro. Precompiled Ahead-of-Time for Google Tensor G5 NPU.",
        filename = "gemma-4-E2B-it_Google_Tensor_G5.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm",
        recommendedRamGb = 8.0
    ),
    EdgeModelSpec(
        id = "gemma_4_e2b",
        name = "Gemma 4 E2B (Thinking)",
        sizeLabel = "2.4 GB",
        description = "Google's flagship edge model. Multimodal with reasoning thinking mode.",
        filename = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
        recommendedRamGb = 8.0
    ),
    EdgeModelSpec(
        id = "gemma_4_e4b",
        name = "Gemma 4 E4B (Thinking)",
        sizeLabel = "3.4 GB",
        description = "Google's 4B flagship edge model. Outstanding logical thinking and audio/vision.",
        filename = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm",
        recommendedRamGb = 12.0
    ),
    EdgeModelSpec(
        id = "gemma_3n_e2b",
        name = "Gemma 3n E2B (Gated)",
        sizeLabel = "3.4 GB",
        description = "Gemma 3n E2B ready for deployment on Android. Requires HF Access Token.",
        filename = "gemma-3n-E2B-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/ba9ca88da013b537b6ed38108be609b8db1c3a16/gemma-3n-E2B-it-int4.litertlm",
        recommendedRamGb = 8.0
    ),
    EdgeModelSpec(
        id = "gemma_3n_e4b",
        name = "Gemma 3n E4B (Gated)",
        sizeLabel = "4.6 GB",
        description = "Gemma 3n E4B ready for deployment on Android. Requires HF Access Token.",
        filename = "gemma-3n-E4B-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/297ed75955702dec3503e00c2c2ecbbf475300bc/gemma-3n-E4B-it-int4.litertlm",
        recommendedRamGb = 12.0
    ),
    EdgeModelSpec(
        id = "gemma_3_1b",
        name = "Gemma 3 1B",
        sizeLabel = "0.5 GB",
        description = "Google's ultra-compact Gemma 3 edge model. Extremely fast and lightweight.",
        filename = "gemma3-1b-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm",
        recommendedRamGb = 6.0
    ),
    EdgeModelSpec(
        id = "qwen_2_5_1_5b",
        name = "Qwen 2.5 1.5B Instruct",
        sizeLabel = "1.5 GB",
        description = "Alibaba Qwen 2.5 1.5B Instruct model optimized for mobile deployment.",
        filename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        recommendedRamGb = 6.0
    ),
    EdgeModelSpec(
        id = "deepseek_r1_1_5b",
        name = "DeepSeek R1 Distill 1.5B",
        sizeLabel = "1.7 GB",
        description = "Distilled reasoning model. Uses local chain-of-thought for task parsing.",
        filename = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/e34bb88632342d1f9640bad579a45134eb1cf988/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        recommendedRamGb = 6.0
    ),
    EdgeModelSpec(
        id = "tiny_garden_270m",
        name = "TinyGarden 270M",
        sizeLabel = "0.27 GB",
        description = "Fine-tuned Function Gemma 270M model for Tiny Garden tasks.",
        filename = "tiny_garden_q8_ekv1024.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/resolve/c205853ff82da86141a1105faa2344a8b176dfe7/tiny_garden_q8_ekv1024.litertlm",
        recommendedRamGb = 6.0
    ),
    EdgeModelSpec(
        id = "mobile_actions_270m_tensor_g5",
        name = "MobileActions 270M (Tensor G5 NPU)",
        sizeLabel = "0.5 GB",
        description = "Fine-tuned Function Gemma 270M model for Mobile Actions tasks precompiled Ahead-of-Time for Google Tensor G5 NPU. Requires HF Access.",
        filename = "functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/main/functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm",
        recommendedRamGb = 6.0
    ),
    EdgeModelSpec(
        id = "mobile_actions_270m",
        name = "MobileActions 270M",
        sizeLabel = "0.27 GB",
        description = "Fine-tuned Function Gemma 270M model for Mobile Actions tasks.",
        filename = "mobile_actions_q8_ekv1024.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/38942192c9b723af836d489074823ff33d4a3e7a/mobile_actions_q8_ekv1024.litertlm",
        recommendedRamGb = 6.0
    )
)
