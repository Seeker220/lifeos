package com.lifeos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lifeos.core.LifeOsLog
import com.lifeos.core.SecretsStore
import com.lifeos.core.model.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedSecretsStore(
    context: Context,
    private val llm: LlmConfig?,
) : SecretsStore {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    override fun llmConfig(): LlmConfig? = llm?.takeIf { it.usable }

    override suspend fun putMailSecret(accountId: String, secret: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key(accountId), secret).apply()
    }

    override suspend fun getMailSecret(accountId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key(accountId), null)
    }

    override suspend fun deleteMailSecret(accountId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key(accountId)).apply()
    }

    private fun key(accountId: String) = "mail_$accountId"

    companion object {
        private const val PREFS_NAME = "lifeos_mail_secrets"

        private fun createPrefs(context: Context): SharedPreferences {
            return runCatching {
                val master = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse { err ->
                LifeOsLog.d("LifeOS/Secrets", "encrypted prefs failed: ${err.message}")
                context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
