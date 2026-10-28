package de.mrt.rich2euro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mrt.rich2euro.R
import de.mrt.rich2euro.model.CoinSide
import de.mrt.rich2euro.ui.AnalysisUiStatus
import de.mrt.rich2euro.ui.Rich2EuroUiState
import de.mrt.rich2euro.ui.components.BiMetallicCoinSlotCard

@Composable
fun AnalyzerScreen(
    uiState: Rich2EuroUiState,
    onPickFromGallery: (CoinSide) -> Unit,
    onTakeCameraPhoto: (CoinSide) -> Unit,
    onRemovePhoto: (CoinSide) -> Unit,
    onSwapSides: () -> Unit,
    onResetSelection: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onDismissError: () -> Unit,
    onEditApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = uiState.status is AnalysisUiStatus.Loading
    val hasAnyPhoto = uiState.obverseUri != null || uiState.reverseUri != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("analyzer_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Hero Image Banner without any text overlay
            item {
                NumismaticHeroBanner()
            }

            // 2. Coin Side Upload Slots (Adaptive: Side-by-Side on Wide Screens, Stacked on Phones)
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWideLayout = maxWidth >= 580.dp
                    if (isWideLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                BiMetallicCoinSlotCard(
                                    side = CoinSide.OBVERSE,
                                    imageUri = uiState.obverseUri,
                                    enabled = !isLoading,
                                    onPickGallery = { onPickFromGallery(CoinSide.OBVERSE) },
                                    onTakePhoto = { onTakeCameraPhoto(CoinSide.OBVERSE) },
                                    onRemovePhoto = { onRemovePhoto(CoinSide.OBVERSE) },
                                    modifier = Modifier.weight(1f)
                                )
                                BiMetallicCoinSlotCard(
                                    side = CoinSide.REVERSE,
                                    imageUri = uiState.reverseUri,
                                    enabled = !isLoading,
                                    onPickGallery = { onPickFromGallery(CoinSide.REVERSE) },
                                    onTakePhoto = { onTakeCameraPhoto(CoinSide.REVERSE) },
                                    onRemovePhoto = { onRemovePhoto(CoinSide.REVERSE) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (hasAnyPhoto) {
                                UtilitySwapAndResetColumn(
                                    isWideLayout = true,
                                    enabled = !isLoading,
                                    onSwapSides = onSwapSides,
                                    onResetSelection = onResetSelection
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BiMetallicCoinSlotCard(
                                side = CoinSide.OBVERSE,
                                imageUri = uiState.obverseUri,
                                enabled = !isLoading,
                                onPickGallery = { onPickFromGallery(CoinSide.OBVERSE) },
                                onTakePhoto = { onTakeCameraPhoto(CoinSide.OBVERSE) },
                                onRemovePhoto = { onRemovePhoto(CoinSide.OBVERSE) }
                            )

                            if (hasAnyPhoto) {
                                UtilitySwapAndResetColumn(
                                    isWideLayout = false,
                                    enabled = !isLoading,
                                    onSwapSides = onSwapSides,
                                    onResetSelection = onResetSelection
                                )
                            }

                            BiMetallicCoinSlotCard(
                                side = CoinSide.REVERSE,
                                imageUri = uiState.reverseUri,
                                enabled = !isLoading,
                                onPickGallery = { onPickFromGallery(CoinSide.REVERSE) },
                                onTakePhoto = { onTakeCameraPhoto(CoinSide.REVERSE) },
                                onRemovePhoto = { onRemovePhoto(CoinSide.REVERSE) }
                            )
                        }
                    }
                }
            }

            // 3. Error Card (if AI call failed or timed out)
            item {
                val errorStatus = uiState.status as? AnalysisUiStatus.Error
                AnimatedVisibility(
                    visible = errorStatus != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (errorStatus != null) {
                        AnalysisErrorCard(
                            errorMessage = errorStatus.message,
                            isApiKeyRelated = errorStatus.isApiKeyRelated,
                            canRetry = uiState.canAnalyze,
                            onRetry = onAnalyzeClick,
                            onEditApiKey = onEditApiKey,
                            onDismiss = onDismissError
                        )
                    }
                }
            }

            // 4. Analyzing Progress Card OR Primary Analyze Button
            item {
                val loadingStatus = uiState.status as? AnalysisUiStatus.Loading
                AnimatedVisibility(
                    visible = loadingStatus != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AnalyzingProgressCard(liveStatus = loadingStatus?.liveStatus)
                }

                AnimatedVisibility(
                    visible = !isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAnalyzeClick,
                            enabled = uiState.canAnalyze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("analyze_coin_button"),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.action_analyze_coin),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (!uiState.canAnalyze) {
                            Text(
                                text = stringResource(R.string.hint_select_both_sides),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NumismaticHeroBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_banner_card"),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_numismatic),
            contentDescription = stringResource(R.string.cd_hero_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
    }
}

@Composable
private fun UtilitySwapAndResetColumn(
    isWideLayout: Boolean,
    enabled: Boolean,
    onSwapSides: () -> Unit,
    onResetSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(
            onClick = onSwapSides,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("swap_sides_button"),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = if (isWideLayout) Icons.Filled.SwapHoriz else Icons.Filled.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.action_swap_sides))
        }

        OutlinedButton(
            onClick = onResetSelection,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("reset_selection_button"),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.action_reset_all))
        }
    }
}

@Composable
private fun AnalyzingProgressCard(
    liveStatus: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analyzing_progress_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.analyzing_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = liveStatus ?: stringResource(R.string.analyzing_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisErrorCard(
    errorMessage: String,
    isApiKeyRelated: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onEditApiKey: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analysis_error_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.error_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dismiss_error_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_dismiss),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                if (isApiKeyRelated) {
                    Button(
                        onClick = onEditApiKey,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("error_edit_api_key_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.action_edit_api_key))
                    }
                } else if (canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("retry_analysis_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(text = stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}
