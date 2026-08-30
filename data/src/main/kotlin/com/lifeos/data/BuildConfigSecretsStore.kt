package com.lifeos.data

import com.lifeos.core.SecretsStore
import com.lifeos.core.model.LlmConfig

class BuildConfigSecretsStore(
    private val config: LlmConfig,
) : SecretsStore {
    override fun llmConfig(): LlmConfig? = config.takeIf { it.usable }
}
