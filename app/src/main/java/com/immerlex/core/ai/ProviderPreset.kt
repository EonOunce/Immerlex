package com.immerlex.core.ai

/**
 * Gateway addresses users keep mistyping. Model IDs are deliberately not invented here
 * beyond DeepSeek's, whose product name and API name differ — the authoritative list
 * comes from [Probe.modelIds] once a key works.
 */
data class ProviderPreset(
    val id: String,
    val baseUrl: String,
    val knownModels: List<String> = emptyList(),
)

val ProviderPresets: List<ProviderPreset> = listOf(
    ProviderPreset(
        id = "deepseek",
        baseUrl = "https://api.deepseek.com",
        // Verified against the live gateway on 2026-10-28; 测试连接 replaces this with whatever
        // the account can actually call.
        knownModels = listOf("deepseek-flash", "deepseek-v4-pro"),
    ),
    ProviderPreset(id = "zhipu", baseUrl = "https://open.bigmodel.cn/api/paas/v4"),
    ProviderPreset(id = "moonshot", baseUrl = "https://api.moonshot.cn/v1"),
    ProviderPreset(id = "dashscope", baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ProviderPreset(id = "openai", baseUrl = "https://api.openai.com/v1"),
    ProviderPreset(id = "openrouter", baseUrl = "https://openrouter.ai/api/v1"),
)
