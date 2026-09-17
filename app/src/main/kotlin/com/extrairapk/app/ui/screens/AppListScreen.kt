package com.extrairapk.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.extrairapk.app.AppListViewModel
import com.extrairapk.app.R
import com.extrairapk.app.UiEvent
import com.extrairapk.app.data.AppFilter
import com.extrairapk.app.data.ExtractionState
import com.extrairapk.app.data.InstalledAppInfo
import com.extrairapk.app.data.SortOrder
import com.extrairapk.app.ui.components.AppDetailSheet
import com.extrairapk.app.ui.components.AppListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val apps by viewModel.visibleApps.collectAsState()
    val extractionStates by viewModel.extractionStatesFlow.collectAsState()

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingDownloadTarget by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = pendingDownloadTarget
        pendingDownloadTarget = null
        if (granted && target != null) {
            viewModel.saveToDownloads(target)
        }
    }

    fun requestSaveToDownloads(app: InstalledAppInfo) {
        val needsRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            pendingDownloadTarget = app
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveToDownloads(app)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is UiEvent.LaunchIntent -> runCatching { context.startActivity(event.intent) }
                is UiEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        if (uiState.isSelectionMode) {
                            Text(stringResource(R.string.selected_count, uiState.selectedPackages.size))
                        } else {
                            Text(stringResource(R.string.app_name))
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSelectionMode) {
                            IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                    },
                    actions = {
                        if (!uiState.isSelectionMode) {
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                            }
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(Icons.Filled.SortByAlpha, contentDescription = stringResource(R.string.sort))
                                }
                                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_name)) },
                                        onClick = { viewModel.onSortChange(SortOrder.NAME); sortMenuExpanded = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_size)) },
                                        onClick = { viewModel.onSortChange(SortOrder.SIZE); sortMenuExpanded = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_recent)) },
                                        onClick = { viewModel.onSortChange(SortOrder.RECENT); sortMenuExpanded = false },
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = { viewModel.exportSelectedToDownloads() }) {
                                Text(stringResource(R.string.export_selected))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                )

                if (searchExpanded && !uiState.isSelectionMode) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    )
                }

                if (!uiState.isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.filter == AppFilter.ALL,
                            onClick = { viewModel.onFilterChange(AppFilter.ALL) },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        FilterChip(
                            selected = uiState.filter == AppFilter.USER,
                            onClick = { viewModel.onFilterChange(AppFilter.USER) },
                            label = { Text(stringResource(R.string.filter_user)) },
                        )
                        FilterChip(
                            selected = uiState.filter == AppFilter.SYSTEM,
                            onClick = { viewModel.onFilterChange(AppFilter.SYSTEM) },
                            label = { Text(stringResource(R.string.filter_system)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.isSelectionMode && uiState.selectedPackages.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.exportSelectedToDownloads() },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    text = { Text(stringResource(R.string.export_selected)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = app.packageName in uiState.selectedPackages,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelected(app.packageName)
                                } else {
                                    selectedApp = app
                                }
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode) viewModel.toggleSelectionMode(true)
                                viewModel.toggleSelected(app.packageName)
                            },
                        )
                    }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AppDetailSheet(
            app = app,
            extractionState = extractionStates[app.packageName] ?: ExtractionState.Idle,
            onDismiss = { selectedApp = null },
            onExtract = { viewModel.extract(app) },
            onShare = { viewModel.share(app) },
            onSaveToDownloads = { requestSaveToDownloads(app) },
            onInstall = { viewModel.install(app) },
        )
    }
}
