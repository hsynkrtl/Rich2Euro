package de.mrt.rich2euro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mrt.rich2euro.R
import de.mrt.rich2euro.model.AppTab
import de.mrt.rich2euro.model.CoinSide
import de.mrt.rich2euro.ui.screens.AnalysisReportScreen
import de.mrt.rich2euro.ui.screens.AnalyzerScreen
import de.mrt.rich2euro.ui.screens.ApiKeySetupScreen
import de.mrt.rich2euro.ui.screens.CoinCameraCaptureScreen
import de.mrt.rich2euro.ui.screens.GuideScreen
import de.mrt.rich2euro.ui.screens.HistoryScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Rich2EuroApp(
    viewModel: CoinAnalyzerViewModel = viewModel(
        factory = CoinAnalyzerViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val deniedCameraMsg = stringResource(R.string.camera_permission_denied)

    LaunchedEffect(uiState.transientMessage) {
        val msg = uiState.transientMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTransientMessage()
        }
    }

    if (!uiState.isApiKeyInitialized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.shouldShowApiKeyScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            ApiKeySetupScreen(
                existingApiKey = uiState.apiKey,
                existingBackupApiKey = uiState.backupApiKey,
                canDismiss = uiState.hasValidSavedApiKey,
                onSaveApiKey = { primary, backup ->
                    viewModel.saveApiKey(primary, backup)
                },
                onDeleteApiKey = viewModel::deleteSavedApiKey,
                onDismiss = viewModel::closeApiKeySettings,
                onShowMessage = viewModel::showTransientMessage
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        return
    }

    var pendingGallerySide by rememberSaveable { mutableStateOf(CoinSide.OBVERSE) }
    var pendingCameraSide by rememberSaveable { mutableStateOf(CoinSide.OBVERSE) }
    var activeCameraSide by rememberSaveable { mutableStateOf<CoinSide?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            viewModel.setCoinImage(pendingGallerySide, selectedUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            activeCameraSide = pendingCameraSide
        } else {
            viewModel.showTransientMessage(deniedCameraMsg)
        }
    }

    val requestGalleryPick: (CoinSide) -> Unit = { side ->
        pendingGallerySide = side
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val requestCameraCapture: (CoinSide) -> Unit = { side ->
        pendingCameraSide = side
        val permissionStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            activeCameraSide = side
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Full-screen In-App Circular Coin Camera View
    val currentCameraSide = activeCameraSide
    if (currentCameraSide != null) {
        CoinCameraCaptureScreen(
            side = currentCameraSide,
            onPhotoCaptured = { croppedCoinUri ->
                viewModel.setCoinImage(currentCameraSide, croppedCoinUri)
                activeCameraSide = null
            },
            onCancel = {
                activeCameraSide = null
            },
            onError = { errorMsg ->
                activeCameraSide = null
                viewModel.showTransientMessage(errorMsg)
            }
        )
        return
    }

    // Full-screen Report View if a report is active
    val activeReport = uiState.activeReport
    if (activeReport != null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            AnalysisReportScreen(
                report = activeReport,
                onBack = viewModel::closeReport,
                onScanAnother = viewModel::startNewScanFromReport,
                modifier = Modifier.padding(innerPadding)
            )
        }
        return
    }

    // Adaptive Shell: NavigationRail on Medium/Expanded (>= 600.dp), NavigationBar on Compact (< 600.dp)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Euro,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::openApiKeySettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_bar_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = stringResource(R.string.cd_manage_api_key),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    Rich2EuroBottomBar(
                        selectedTab = uiState.selectedTab,
                        historyCount = uiState.sessionHistory.size,
                        onSelectTab = viewModel::selectTab
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    Rich2EuroSideRail(
                        selectedTab = uiState.selectedTab,
                        historyCount = uiState.sessionHistory.size,
                        onSelectTab = viewModel::selectTab
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = uiState.selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab_content_transition"
                    ) { currentTab ->
                        when (currentTab) {
                            AppTab.ANALYZE -> AnalyzerScreen(
                                uiState = uiState,
                                onPickFromGallery = requestGalleryPick,
                                onTakeCameraPhoto = requestCameraCapture,
                                onRemovePhoto = { side -> viewModel.setCoinImage(side, null) },
                                onSwapSides = viewModel::swapCoinSides,
                                onResetSelection = viewModel::resetSelection,
                                onAnalyzeClick = { viewModel.analyzeCoin(context) },
                                onDismissError = viewModel::dismissError,
                                onEditApiKey = viewModel::openApiKeySettings
                            )

                            AppTab.HISTORY -> HistoryScreen(
                                history = uiState.sessionHistory,
                                onOpenReport = viewModel::openReport,
                                onNavigateToAnalyzer = { viewModel.selectTab(AppTab.ANALYZE) }
                            )

                            AppTab.GUIDE -> GuideScreen(
                                onNavigateBackToAnalyzer = { viewModel.selectTab(AppTab.ANALYZE) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Rich2EuroBottomBar(
    selectedTab: AppTab,
    historyCount: Int,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.ANALYZE,
            onClick = { onSelectTab(AppTab.ANALYZE) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.ANALYZE) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.nav_analyze)
                )
            },
            label = { Text(text = stringResource(R.string.nav_analyze)) },
            modifier = Modifier.testTag("tab_analyze")
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.HISTORY,
            onClick = { onSelectTab(AppTab.HISTORY) },
            icon = {
                BadgedBox(
                    badge = {
                        if (historyCount > 0) {
                            Badge { Text(text = historyCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedTab == AppTab.HISTORY) Icons.Filled.HistoryEdu else Icons.Outlined.HistoryEdu,
                        contentDescription = stringResource(R.string.nav_history)
                    )
                }
            },
            label = { Text(text = stringResource(R.string.nav_history)) },
            modifier = Modifier.testTag("tab_history")
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.GUIDE,
            onClick = { onSelectTab(AppTab.GUIDE) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.GUIDE) Icons.Filled.AutoStories else Icons.Outlined.AutoStories,
                    contentDescription = stringResource(R.string.nav_guide)
                )
            },
            label = { Text(text = stringResource(R.string.nav_guide)) },
            modifier = Modifier.testTag("tab_guide")
        )
    }
}

@Composable
private fun Rich2EuroSideRail(
    selectedTab: AppTab,
    historyCount: Int,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(modifier = Modifier.weight(1f))

        NavigationRailItem(
            selected = selectedTab == AppTab.ANALYZE,
            onClick = { onSelectTab(AppTab.ANALYZE) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.ANALYZE) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.nav_analyze)
                )
            },
            label = { Text(text = stringResource(R.string.nav_analyze)) },
            modifier = Modifier.testTag("rail_analyze")
        )

        NavigationRailItem(
            selected = selectedTab == AppTab.HISTORY,
            onClick = { onSelectTab(AppTab.HISTORY) },
            icon = {
                BadgedBox(
                    badge = {
                        if (historyCount > 0) {
                            Badge { Text(text = historyCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedTab == AppTab.HISTORY) Icons.Filled.HistoryEdu else Icons.Outlined.HistoryEdu,
                        contentDescription = stringResource(R.string.nav_history)
                    )
                }
            },
            label = { Text(text = stringResource(R.string.nav_history)) },
            modifier = Modifier.testTag("rail_history")
        )

        NavigationRailItem(
            selected = selectedTab == AppTab.GUIDE,
            onClick = { onSelectTab(AppTab.GUIDE) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.GUIDE) Icons.Filled.AutoStories else Icons.Outlined.AutoStories,
                    contentDescription = stringResource(R.string.nav_guide)
                )
            },
            label = { Text(text = stringResource(R.string.nav_guide)) },
            modifier = Modifier.testTag("rail_guide")
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}
