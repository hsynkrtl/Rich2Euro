package de.mrt.rich2euro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rich2euro_user_prefs"
)

data class StoredApiKeys(
    val primaryKey: String? = null,
    val backupKey: String? = null
)

interface ApiKeyStore {
    val apiKeysFlow: Flow<StoredApiKeys>
    suspend fun saveApiKeys(primaryKey: String, backupKey: String? = null)
    suspend fun clearApiKeys()
}

class DataStoreApiKeyRepository(
    private val context: Context
) : ApiKeyStore {

    private companion object {
        val USER_PRIMARY_API_KEY = stringPreferencesKey("user_gemini_api_key")
        val USER_BACKUP_API_KEY = stringPreferencesKey("user_backup_api_key")
    }

    override val apiKeysFlow: Flow<StoredApiKeys> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            StoredApiKeys(
                primaryKey = prefs[USER_PRIMARY_API_KEY]?.trim()?.takeIf { it.isNotEmpty() },
                backupKey = prefs[USER_BACKUP_API_KEY]?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

    override suspend fun saveApiKeys(primaryKey: String, backupKey: String?) {
        val cleanPrimary = primaryKey.trim()
        val cleanBackup = backupKey?.trim().orEmpty()
        context.userPreferencesDataStore.edit { prefs ->
            if (cleanPrimary.isEmpty()) {
                prefs.remove(USER_PRIMARY_API_KEY)
            } else {
                prefs[USER_PRIMARY_API_KEY] = cleanPrimary
            }

            if (cleanBackup.isEmpty()) {
                prefs.remove(USER_BACKUP_API_KEY)
            } else {
                prefs[USER_BACKUP_API_KEY] = cleanBackup
            }
        }
    }

    override suspend fun clearApiKeys() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs.remove(USER_PRIMARY_API_KEY)
            prefs.remove(USER_BACKUP_API_KEY)
        }
    }
}

class InMemoryApiKeyStore(
    initialPrimaryKey: String? = null,
    initialBackupKey: String? = null
) : ApiKeyStore {
    private val state = MutableStateFlow(
        StoredApiKeys(
            primaryKey = initialPrimaryKey?.trim()?.takeIf { it.isNotEmpty() },
            backupKey = initialBackupKey?.trim()?.takeIf { it.isNotEmpty() }
        )
    )
    override val apiKeysFlow: Flow<StoredApiKeys> = state.asStateFlow()

    override suspend fun saveApiKeys(primaryKey: String, backupKey: String?) {
        state.value = StoredApiKeys(
            primaryKey = primaryKey.trim().takeIf { it.isNotEmpty() },
            backupKey = backupKey?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    override suspend fun clearApiKeys() {
        state.value = StoredApiKeys()
    }
}
