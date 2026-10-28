package de.mrt.rich2euro.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mrt.rich2euro.data.ApiKeyStore
import de.mrt.rich2euro.data.CoinAnalyzerRepository
import de.mrt.rich2euro.data.DataStoreApiKeyRepository
import de.mrt.rich2euro.data.InMemoryApiKeyStore
import de.mrt.rich2euro.data.InvalidApiKeyException
import de.mrt.rich2euro.model.AppTab
import de.mrt.rich2euro.model.CoinAnalysisResult
import de.mrt.rich2euro.model.CoinSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CoinAnalyzerViewModel"

sealed interface AnalysisUiStatus {
    data object Idle : AnalysisUiStatus
    data class Loading(val liveStatus: String? = null) : AnalysisUiStatus
    data class Error(
        val message: String,
        val isApiKeyRelated: Boolean = false
    ) : AnalysisUiStatus
}

data class Rich2EuroUiState(
    val isApiKeyInitialized: Boolean = false,
    val apiKey: String? = null,
    val backupApiKey: String? = null,
    val isEditingApiKey: Boolean = false,
    val selectedTab: AppTab = AppTab.ANALYZE,
    val obverseUri: Uri? = null,
    val reverseUri: Uri? = null,
    val status: AnalysisUiStatus = AnalysisUiStatus.Idle,
    val activeReport: CoinAnalysisResult? = null,
    val sessionHistory: List<CoinAnalysisResult> = emptyList(),
    val transientMessage: String? = null
) {
    val hasValidSavedApiKey: Boolean
        get() = !apiKey.isNullOrBlank()

    val shouldShowApiKeyScreen: Boolean
        get() = isApiKeyInitialized && (!hasValidSavedApiKey || isEditingApiKey)

    val canAnalyze: Boolean
        get() = hasValidSavedApiKey &&
            obverseUri != null &&
            reverseUri != null &&
            status !is AnalysisUiStatus.Loading
}

class CoinAnalyzerViewModel(
    private val apiKeyStore: ApiKeyStore = InMemoryApiKeyStore(),
    private val repository: CoinAnalyzerRepository = CoinAnalyzerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(Rich2EuroUiState())
    val uiState: StateFlow<Rich2EuroUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            apiKeyStore.apiKeysFlow.collect { stored ->
                _uiState.update { state ->
                    state.copy(
                        isApiKeyInitialized = true,
                        apiKey = stored.primaryKey,
                        backupApiKey = stored.backupKey
                    )
                }
            }
        }
    }

    fun saveApiKey(rawKey: String, rawBackupKey: String? = null) {
        val cleanedPrimary = rawKey.trim()
        if (cleanedPrimary.isEmpty()) return
        val cleanedBackup = rawBackupKey?.trim()?.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            apiKeyStore.saveApiKeys(
                primaryKey = cleanedPrimary,
                backupKey = cleanedBackup
            )
            _uiState.update { state ->
                val clearedErrorStatus = if (state.status is AnalysisUiStatus.Error) {
                    AnalysisUiStatus.Idle
                } else {
                    state.status
                }
                state.copy(
                    apiKey = cleanedPrimary,
                    backupApiKey = cleanedBackup,
                    isEditingApiKey = false,
                    status = clearedErrorStatus
                )
            }
        }
    }

    fun deleteSavedApiKey() {
        viewModelScope.launch {
            apiKeyStore.clearApiKeys()
            _uiState.update { state ->
                state.copy(
                    apiKey = null,
                    backupApiKey = null,
                    isEditingApiKey = false,
                    activeReport = null
                )
            }
        }
    }

    fun openApiKeySettings() {
        _uiState.update { it.copy(isEditingApiKey = true) }
    }

    fun closeApiKeySettings() {
        _uiState.update { state ->
            if (state.hasValidSavedApiKey) {
                state.copy(isEditingApiKey = false)
            } else {
                state
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
                activeReport = null
            )
        }
    }

    fun setCoinImage(side: CoinSide, uri: Uri?) {
        _uiState.update { state ->
            val updated = when (side) {
                CoinSide.OBVERSE -> state.copy(obverseUri = uri)
                CoinSide.REVERSE -> state.copy(reverseUri = uri)
            }
            if (state.status is AnalysisUiStatus.Error) {
                updated.copy(status = AnalysisUiStatus.Idle)
            } else {
                updated
            }
        }
    }

    fun swapCoinSides() {
        _uiState.update { state ->
            state.copy(
                obverseUri = state.reverseUri,
                reverseUri = state.obverseUri
            )
        }
    }

    fun resetSelection() {
        _uiState.update { state ->
            state.copy(
                obverseUri = null,
                reverseUri = null,
                status = AnalysisUiStatus.Idle,
                activeReport = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { state ->
            if (state.status is AnalysisUiStatus.Error) {
                state.copy(status = AnalysisUiStatus.Idle)
            } else {
                state
            }
        }
    }

    fun showTransientMessage(message: String) {
        _uiState.update { it.copy(transientMessage = message) }
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    fun openReport(report: CoinAnalysisResult) {
        _uiState.update { it.copy(activeReport = report) }
    }

    fun closeReport() {
        _uiState.update { it.copy(activeReport = null) }
    }

    fun startNewScanFromReport() {
        _uiState.update { state ->
            state.copy(
                selectedTab = AppTab.ANALYZE,
                obverseUri = null,
                reverseUri = null,
                status = AnalysisUiStatus.Idle,
                activeReport = null
            )
        }
    }

    fun analyzeCoin(context: Context) {
        val currentState = _uiState.value
        val activeApiKey = currentState.apiKey?.trim().orEmpty()
        if (activeApiKey.isEmpty()) {
            _uiState.update { it.copy(isEditingApiKey = true) }
            return
        }
        val obverse = currentState.obverseUri ?: return
        val reverse = currentState.reverseUri ?: return
        if (currentState.status is AnalysisUiStatus.Loading) return

        val backupKey = currentState.backupApiKey
        val appContext = context.applicationContext

        viewModelScope.launch {
            _uiState.update { it.copy(status = AnalysisUiStatus.Loading()) }
            try {
                val result = repository.analyzeCoinImages(
                    context = appContext,
                    primaryApiKey = activeApiKey,
                    backupApiKey = backupKey,
                    obverseUri = obverse,
                    reverseUri = reverse,
                    onStatusUpdate = { statusMsg ->
                        _uiState.update { s ->
                            if (s.status is AnalysisUiStatus.Loading) {
                                s.copy(status = AnalysisUiStatus.Loading(statusMsg))
                            } else {
                                s
                            }
                        }
                    }
                )
                _uiState.update { state ->
                    state.copy(
                        status = AnalysisUiStatus.Idle,
                        activeReport = result,
                        sessionHistory = listOf(result) + state.sessionHistory
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Multi-server failover budget exhausted", e)
                _uiState.update { state ->
                    state.copy(
                        status = AnalysisUiStatus.Error(
                            message = "Alle Analyse-Server haben das Zeitlimit überschritten. Bitte prüfe deine Internetverbindung und tippe auf „Erneut versuchen“.",
                            isApiKeyRelated = false
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidApiKeyException) {
                Log.e(TAG, "Invalid user API key", e)
                _uiState.update { state ->
                    state.copy(
                        status = AnalysisUiStatus.Error(
                            message = e.message ?: "Ungültiger API-Key.",
                            isApiKeyRelated = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Coin analysis failed across servers", e)
                val detailedMessage = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Unbekannter Fehler bei der KI-Münzanalyse."
                _uiState.update { state ->
                    state.copy(
                        status = AnalysisUiStatus.Error(
                            message = detailedMessage,
                            isApiKeyRelated = false
                        )
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            val appContext = context.applicationContext
            initializer {
                CoinAnalyzerViewModel(
                    apiKeyStore = DataStoreApiKeyRepository(appContext),
                    repository = CoinAnalyzerRepository()
                )
            }
        }
    }
}
