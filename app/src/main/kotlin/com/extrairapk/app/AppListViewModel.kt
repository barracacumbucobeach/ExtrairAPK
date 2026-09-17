package com.extrairapk.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.extrairapk.app.data.AppFilter
import com.extrairapk.app.data.AppRepository
import com.extrairapk.app.data.ExtractionState
import com.extrairapk.app.data.InstalledAppInfo
import com.extrairapk.app.data.SortOrder
import com.extrairapk.app.util.ApkExtractor
import com.extrairapk.app.util.ExportUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NAME,
    val isSelectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
)

sealed interface UiEvent {
    data class LaunchIntent(val intent: Intent) : UiEvent
    data class ShowMessage(val message: String) : UiEvent
}

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val allApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val uiState = MutableStateFlow(AppListUiState())
    private val extractionStates = MutableStateFlow<Map<String, ExtractionState>>(emptyMap())

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<AppListUiState> = uiState

    val visibleApps: StateFlow<List<InstalledAppInfo>> = combine(allApps, uiState) { apps, ui ->
        apps
            .asSequence()
            .filter { app ->
                when (ui.filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> app.isUserApp
                    AppFilter.SYSTEM -> !app.isUserApp
                }
            }
            .filter { app ->
                ui.query.isBlank() ||
                    app.label.contains(ui.query, ignoreCase = true) ||
                    app.packageName.contains(ui.query, ignoreCase = true)
            }
            .sortedWith(
                when (ui.sortOrder) {
                    SortOrder.NAME -> compareBy { it.label.lowercase() }
                    SortOrder.SIZE -> compareByDescending { it.totalApkBytes }
                    SortOrder.RECENT -> compareByDescending { it.updatedAt }
                },
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val extractionStatesFlow: StateFlow<Map<String, ExtractionState>> = extractionStates

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isLoading = true)
            val apps = repository.loadInstalledApps()
            allApps.value = apps
            uiState.value = uiState.value.copy(isLoading = false)
        }
    }

    fun onQueryChange(query: String) {
        uiState.value = uiState.value.copy(query = query)
    }

    fun onFilterChange(filter: AppFilter) {
        uiState.value = uiState.value.copy(filter = filter)
    }

    fun onSortChange(sortOrder: SortOrder) {
        uiState.value = uiState.value.copy(sortOrder = sortOrder)
    }

    fun toggleSelectionMode(enabled: Boolean) {
        uiState.value = uiState.value.copy(
            isSelectionMode = enabled,
            selectedPackages = if (enabled) uiState.value.selectedPackages else emptySet(),
        )
    }

    fun toggleSelected(packageName: String) {
        val current = uiState.value.selectedPackages
        val updated = if (packageName in current) current - packageName else current + packageName
        uiState.value = uiState.value.copy(selectedPackages = updated)
    }

    fun extract(app: InstalledAppInfo) {
        viewModelScope.launch {
            extractionStates.value = extractionStates.value + (app.packageName to ExtractionState.InProgress)
            val result = ApkExtractor.extract(getApplication(), app)
            extractionStates.value = extractionStates.value + (
                app.packageName to result.fold(
                    onSuccess = { ExtractionState.Done(it) },
                    onFailure = { ExtractionState.Error(it.message ?: "Erro desconhecido") },
                )
            )
            result.onFailure {
                events.trySend(UiEvent.ShowMessage(getApplication<Application>().getString(R.string.extract_failed)))
            }
        }
    }

    fun share(app: InstalledAppInfo) {
        val done = extractionStates.value[app.packageName] as? ExtractionState.Done ?: return
        val intent = ExportUtils.shareIntent(getApplication(), done.result.outputFile, done.result.isSplitBundle)
        events.trySend(UiEvent.LaunchIntent(Intent.createChooser(intent, app.label)))
    }

    fun install(app: InstalledAppInfo) {
        val done = extractionStates.value[app.packageName] as? ExtractionState.Done ?: return
        if (done.result.isSplitBundle) {
            runCatching { ExportUtils.installSplitBundle(getApplication(), done.result.componentApks) }
                .onFailure {
                    events.trySend(UiEvent.ShowMessage(getApplication<Application>().getString(R.string.install_failed)))
                }
        } else {
            val intent = ExportUtils.installSingleApkIntent(getApplication(), done.result.outputFile)
            events.trySend(UiEvent.LaunchIntent(intent))
        }
    }

    fun saveToDownloads(app: InstalledAppInfo) {
        val done = extractionStates.value[app.packageName] as? ExtractionState.Done ?: return
        viewModelScope.launch {
            val mime = ExportUtils.mimeTypeFor(done.result.isSplitBundle)
            val result = ExportUtils.saveToDownloads(getApplication(), done.result.outputFile, mime)
            val app1 = getApplication<Application>()
            val message = result.fold(
                onSuccess = { app1.getString(R.string.saved_to_downloads, done.result.outputFile.name) },
                onFailure = { app1.getString(R.string.save_failed) },
            )
            events.trySend(UiEvent.ShowMessage(message))
        }
    }

    fun exportSelectedToDownloads() {
        val selected = uiState.value.selectedPackages
        val apps = allApps.value.filter { it.packageName in selected }
        viewModelScope.launch {
            var successCount = 0
            apps.forEach { app ->
                extractionStates.value = extractionStates.value + (app.packageName to ExtractionState.InProgress)
                val extracted = ApkExtractor.extract(getApplication(), app)
                extracted.onSuccess { result ->
                    extractionStates.value = extractionStates.value + (app.packageName to ExtractionState.Done(result))
                    val mime = ExportUtils.mimeTypeFor(result.isSplitBundle)
                    val saved = ExportUtils.saveToDownloads(getApplication(), result.outputFile, mime)
                    if (saved.isSuccess) successCount++
                }
                extracted.onFailure {
                    extractionStates.value = extractionStates.value + (
                        app.packageName to ExtractionState.Error(it.message ?: "Erro")
                    )
                }
            }
            val app1 = getApplication<Application>()
            events.trySend(UiEvent.ShowMessage(app1.getString(R.string.batch_export_done, successCount, apps.size)))
            toggleSelectionMode(false)
        }
    }
}
