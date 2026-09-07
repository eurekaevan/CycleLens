package com.eureka.cyclelens.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eureka.cyclelens.CardPickerItemUiState
import com.eureka.cyclelens.CycleTrackerViewModel
import com.eureka.cyclelens.QuickCardOptionUiState
import com.eureka.cyclelens.R
import com.eureka.cyclelens.TrackedCardUiState
import com.eureka.cyclelens.TrackerUiState
import com.eureka.cyclelens.catalog.CardArtworkRepository
import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.capture.CaptureState
import com.eureka.cyclelens.capture.CaptureProfile
import com.eureka.cyclelens.capture.AnalysisDelay
import com.eureka.cyclelens.capture.DebugSnapshotState
import com.eureka.cyclelens.BuildConfig
import com.eureka.cyclelens.overlay.OverlayBackgroundOpacity
import com.eureka.cyclelens.overlay.OverlayConfiguration
import com.eureka.cyclelens.overlay.OverlayDetailMode
import com.eureka.cyclelens.overlay.OverlayQuickCards
import com.eureka.cyclelens.overlay.OverlaySizeMode
import com.eureka.cyclelens.session.MatchSession
import cyclelens.core.CardId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@Composable
fun CycleTrackerRoute(
    matchSession: MatchSession,
    catalog: CardCatalog,
    artworkRepository: CardArtworkRepository,
    overlayQuickCards: OverlayQuickCards,
    overlayConfiguration: OverlayConfiguration,
    captureState: StateFlow<CaptureState>,
    captureProfile: StateFlow<CaptureProfile>,
    analysisDelay: StateFlow<AnalysisDelay>,
    debugSnapshotState: StateFlow<DebugSnapshotState>,
    overlayPermissionGranted: Boolean,
    onEnableOverlayClick: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStartCaptureClick: () -> Unit,
    onStopCaptureClick: () -> Unit,
    onCaptureProfileChanged: (CaptureProfile) -> Unit,
    onAnalysisDelayChanged: (AnalysisDelay) -> Unit,
    onSaveDebugFrameClick: () -> Unit,
    onDeleteDebugFrameClick: () -> Unit,
    trackerViewModel: CycleTrackerViewModel = viewModel(
        factory = CycleTrackerViewModel.Factory(
            matchSession = matchSession,
            catalog = catalog,
            overlayQuickCards = overlayQuickCards,
            overlayConfiguration = overlayConfiguration,
        ),
    ),
) {
    val state by trackerViewModel.uiState.collectAsStateWithLifecycle()
    val currentCaptureState by captureState.collectAsStateWithLifecycle()
    val currentCaptureProfile by captureProfile.collectAsStateWithLifecycle()
    val currentAnalysisDelay by analysisDelay.collectAsStateWithLifecycle()
    val currentDebugSnapshotState by debugSnapshotState.collectAsStateWithLifecycle()

    CycleTrackerScreen(
        state = state,
        artworkRepository = artworkRepository,
        captureState = currentCaptureState,
        captureProfile = currentCaptureProfile,
        analysisDelay = currentAnalysisDelay,
        debugSnapshotState = currentDebugSnapshotState,
        onTrackedCardClick = trackerViewModel::observeTrackedCard,
        onAddCardClick = trackerViewModel::openCardPicker,
        onPickerDismiss = trackerViewModel::dismissCardPicker,
        onSearchQueryChanged = trackerViewModel::onSearchQueryChanged,
        onNewCardClick = trackerViewModel::selectNewCard,
        onQuickCardEditorClick = trackerViewModel::openQuickCardEditor,
        onQuickCardEditorDismiss = trackerViewModel::dismissQuickCardEditor,
        onQuickCardSearchQueryChanged = trackerViewModel::onQuickCardSearchQueryChanged,
        onQuickCardToggle = trackerViewModel::toggleQuickCard,
        onOverlaySizeModeChanged = trackerViewModel::setOverlaySizeMode,
        onOverlayDetailModeChanged = trackerViewModel::setOverlayDetailMode,
        onOverlayBackgroundOpacityChanged = trackerViewModel::setOverlayBackgroundOpacity,
        onUndoClick = trackerViewModel::undo,
        onResetClick = trackerViewModel::requestReset,
        onResetDismiss = trackerViewModel::dismissResetConfirmation,
        onResetConfirm = trackerViewModel::confirmReset,
        overlayPermissionGranted = overlayPermissionGranted,
        onEnableOverlayClick = onEnableOverlayClick,
        onStartOverlayClick = onStartOverlayClick,
        onStartCaptureClick = onStartCaptureClick,
        onStopCaptureClick = onStopCaptureClick,
        onCaptureProfileChanged = onCaptureProfileChanged,
        onAnalysisDelayChanged = onAnalysisDelayChanged,
        onSaveDebugFrameClick = onSaveDebugFrameClick,
        onDeleteDebugFrameClick = onDeleteDebugFrameClick,
    )
}

@Composable
fun CycleTrackerScreen(
    state: TrackerUiState,
    artworkRepository: CardArtworkRepository,
    captureState: CaptureState,
    captureProfile: CaptureProfile,
    analysisDelay: AnalysisDelay,
    debugSnapshotState: DebugSnapshotState,
    onTrackedCardClick: (CardId) -> Unit,
    onAddCardClick: () -> Unit,
    onPickerDismiss: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onNewCardClick: (CardId) -> Unit,
    onQuickCardEditorClick: () -> Unit,
    onQuickCardEditorDismiss: () -> Unit,
    onQuickCardSearchQueryChanged: (String) -> Unit,
    onQuickCardToggle: (CardId) -> Unit,
    onOverlaySizeModeChanged: (OverlaySizeMode) -> Unit,
    onOverlayDetailModeChanged: (OverlayDetailMode) -> Unit,
    onOverlayBackgroundOpacityChanged: (OverlayBackgroundOpacity) -> Unit,
    onUndoClick: () -> Unit,
    onResetClick: () -> Unit,
    onResetDismiss: () -> Unit,
    onResetConfirm: () -> Unit,
    overlayPermissionGranted: Boolean,
    onEnableOverlayClick: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStartCaptureClick: () -> Unit,
    onStopCaptureClick: () -> Unit,
    onCaptureProfileChanged: (CaptureProfile) -> Unit,
    onAnalysisDelayChanged: (AnalysisDelay) -> Unit,
    onSaveDebugFrameClick: () -> Unit,
    onDeleteDebugFrameClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )

            OverlayControl(
                permissionGranted = overlayPermissionGranted,
                sizeMode = state.overlaySizeMode,
                detailMode = state.overlayDetailMode,
                backgroundOpacity = state.overlayBackgroundOpacity,
                onEnableClick = onEnableOverlayClick,
                onStartClick = onStartOverlayClick,
                onSizeModeChanged = onOverlaySizeModeChanged,
                onDetailModeChanged = onOverlayDetailModeChanged,
                onBackgroundOpacityChanged = onOverlayBackgroundOpacityChanged,
            )

            CaptureControl(
                state = captureState,
                profile = captureProfile,
                analysisDelay = analysisDelay,
                debugSnapshotState = debugSnapshotState,
                onStartClick = onStartCaptureClick,
                onStopClick = onStopCaptureClick,
                onProfileChanged = onCaptureProfileChanged,
                onAnalysisDelayChanged = onAnalysisDelayChanged,
                onSaveDebugFrameClick = onSaveDebugFrameClick,
                onDeleteDebugFrameClick = onDeleteDebugFrameClick,
            )

            OverlayQuickCardsControl(
                selectedCount = state.quickCardCount,
                limit = state.quickCardLimit,
                onConfigureClick = onQuickCardEditorClick,
            )

            OpponentDeckHeader(state)

            if (state.opponentDeck.isEmpty()) {
                EmptyDeckMessage()
            } else {
                OpponentDeckGrid(
                    cards = state.opponentDeck,
                    artworkRepository = artworkRepository,
                    onCardClick = onTrackedCardClick,
                )
            }

            Button(
                onClick = onAddCardClick,
                enabled = state.canAddCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    text = if (state.canAddCard) {
                        stringResource(R.string.add_card)
                    } else {
                        stringResource(R.string.deck_full)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onUndoClick,
                    enabled = state.canUndo,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.undo))
                }
                OutlinedButton(
                    onClick = onResetClick,
                    enabled = state.canReset,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.reset))
                }
            }

            FanContentDisclaimer()
        }
    }

    if (state.isCardPickerVisible) {
        CardPickerSheet(
            cards = state.pickerCards,
            artworkRepository = artworkRepository,
            searchQuery = state.searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onCardClick = onNewCardClick,
            onDismiss = onPickerDismiss,
        )
    }

    if (state.isQuickCardEditorVisible) {
        QuickCardEditorSheet(
            cards = state.quickCardOptions,
            artworkRepository = artworkRepository,
            selectedCount = state.quickCardCount,
            limit = state.quickCardLimit,
            searchQuery = state.quickCardSearchQuery,
            onSearchQueryChanged = onQuickCardSearchQueryChanged,
            onCardToggle = onQuickCardToggle,
            onDismiss = onQuickCardEditorDismiss,
        )
    }

    if (state.isResetConfirmationVisible) {
        ResetConfirmationDialog(
            onDismiss = onResetDismiss,
            onConfirm = onResetConfirm,
        )
    }
}

@Composable
private fun CaptureControl(
    state: CaptureState,
    profile: CaptureProfile,
    analysisDelay: AnalysisDelay,
    debugSnapshotState: DebugSnapshotState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onProfileChanged: (CaptureProfile) -> Unit,
    onAnalysisDelayChanged: (AnalysisDelay) -> Unit,
    onSaveDebugFrameClick: () -> Unit,
    onDeleteDebugFrameClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_capture),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.capture_profile),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureProfile.entries.forEach { option ->
                    FilterChip(
                        selected = profile == option,
                        enabled = state == CaptureState.Idle || state is CaptureState.Error,
                        onClick = { onProfileChanged(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        CaptureProfile.NATIVE -> R.string.capture_profile_native
                                        CaptureProfile.BALANCED -> R.string.capture_profile_balanced
                                        CaptureProfile.ECO -> R.string.capture_profile_eco
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(
                    when (state) {
                        CaptureState.Idle -> R.string.capture_status_idle
                        CaptureState.RequestingConsent -> R.string.capture_status_requesting
                        CaptureState.Starting -> R.string.capture_status_starting
                        is CaptureState.Running -> R.string.capture_status_running
                        is CaptureState.Error -> R.string.capture_status_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            when (state) {
                is CaptureState.Running -> CaptureStatsContent(state)
                is CaptureState.Error -> Text(
                    text = stringResource(R.string.capture_error_message, state.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Unit
            }

            Button(
                onClick = if (state is CaptureState.Running) onStopClick else onStartClick,
                enabled = state == CaptureState.Idle ||
                    state is CaptureState.Error ||
                    state is CaptureState.Running,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        when (state) {
                            is CaptureState.Running -> R.string.stop_capture
                            CaptureState.RequestingConsent,
                            CaptureState.Starting,
                            -> R.string.capture_busy

                            else -> R.string.start_capture
                        },
                    ),
                )
            }

            if (BuildConfig.DEBUG) {
                Text(
                    text = stringResource(R.string.analysis_delay),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnalysisDelay.entries.forEach { option ->
                        FilterChip(
                            selected = analysisDelay == option,
                            onClick = { onAnalysisDelayChanged(option) },
                            label = {
                                Text(stringResource(R.string.analysis_delay_value, option.milliseconds))
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = onSaveDebugFrameClick,
                    enabled = state is CaptureState.Running && !debugSnapshotState.pending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (debugSnapshotState.pending) {
                                R.string.debug_snapshot_pending
                            } else {
                                R.string.save_debug_frame
                            },
                        ),
                    )
                }
                debugSnapshotState.cachePath?.let { path ->
                    Text(
                        text = stringResource(R.string.debug_snapshot_path, path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDeleteDebugFrameClick) {
                        Text(stringResource(R.string.delete_debug_frame))
                    }
                }
                debugSnapshotState.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CaptureStatsContent(state: CaptureState.Running) {
    val stats = state.stats
    val unknown = stringResource(R.string.capture_value_unknown)
    Text(
        text = stringResource(
            R.string.analysis_rates,
            stats.analysis.copiedFps,
            stats.analysis.processedFps,
            stats.analysis.copiedFrames,
            stats.analysis.processedFrames,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.analysis_drops,
            stats.analysis.poolMissDrops,
            stats.analysis.queueDrops,
            stats.analysis.copyFailureDrops,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.analysis_timings,
            stats.analysis.averageCopyTimeMs,
            stats.analysis.maxCopyTimeMs,
            stats.analysis.averageProcessingTimeMs,
            stats.analysis.maxProcessingTimeMs,
            stats.analysis.averageQueueLatencyMs,
            stats.analysis.maxQueueLatencyMs,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.analysis_pool,
            stats.analysis.poolAvailable,
            stats.analysis.poolCapacity,
            stats.analysis.poolInUse,
            stats.analysis.queueSize,
            stats.analysis.bufferByteSize,
            stats.analysis.totalPoolByteSize,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (BuildConfig.DEBUG) {
        stats.analysis.lastDebugChecksum?.let { checksum ->
            Text(
                text = stringResource(R.string.analysis_checksum, checksum),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        text = stringResource(
            R.string.capture_source_output_size,
            stats.sourceWidth,
            stats.sourceHeight,
            stats.width,
            stats.height,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.capture_fps,
            stats.incomingFps,
            stats.acceptedFps,
            stats.averageIncomingFps,
            stats.averageAcceptedFps,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.capture_frames,
            stats.receivedFrames,
            stats.acceptedFrames,
            stats.droppedFrames,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.capture_visible,
            stringResource(
                if (stats.capturedContentVisible) R.string.capture_yes else R.string.capture_no,
            ),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.capture_frame_layout,
            stats.planeCount?.toString() ?: unknown,
            stats.pixelStride?.toString() ?: unknown,
            stats.rowStride?.toString() ?: unknown,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.capture_frame_rate_hint,
            stringResource(
                if (stats.surfaceFrameRateHintApplied) R.string.capture_yes else R.string.capture_no,
            ),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OverlayControl(
    permissionGranted: Boolean,
    sizeMode: OverlaySizeMode,
    detailMode: OverlayDetailMode,
    backgroundOpacity: OverlayBackgroundOpacity,
    onEnableClick: () -> Unit,
    onStartClick: () -> Unit,
    onSizeModeChanged: (OverlaySizeMode) -> Unit,
    onDetailModeChanged: (OverlayDetailMode) -> Unit,
    onBackgroundOpacityChanged: (OverlayBackgroundOpacity) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.manual_overlay),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            if (permissionGranted) {
                                R.string.overlay_permission_ready
                            } else {
                                R.string.overlay_permission_required
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = if (permissionGranted) onStartClick else onEnableClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (permissionGranted) {
                                R.string.start_overlay
                            } else {
                                R.string.enable_overlay
                            },
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.overlay_size),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlaySizeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sizeMode == mode,
                        onClick = { onSizeModeChanged(mode) },
                        label = {
                            Text(
                                stringResource(
                                    if (mode == OverlaySizeMode.COMFORTABLE) {
                                        R.string.overlay_size_comfortable
                                    } else {
                                        R.string.overlay_size_compact
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.overlay_detail),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayDetailMode.entries.forEach { mode ->
                    FilterChip(
                        selected = detailMode == mode,
                        onClick = { onDetailModeChanged(mode) },
                        label = {
                            Text(
                                stringResource(
                                    if (mode == OverlayDetailMode.FULL) {
                                        R.string.overlay_detail_full
                                    } else {
                                        R.string.overlay_detail_minimal
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.overlay_background),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayBackgroundOpacity.entries.forEach { opacity ->
                    FilterChip(
                        selected = backgroundOpacity == opacity,
                        onClick = { onBackgroundOpacityChanged(opacity) },
                        label = { Text(stringResource(R.string.percent_value, opacity.percent)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayQuickCardsControl(
    selectedCount: Int,
    limit: Int,
    onConfigureClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.overlay_quick_cards),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.quick_cards_count, selectedCount, limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onConfigureClick,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.configure))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCardEditorSheet(
    cards: List<QuickCardOptionUiState>,
    artworkRepository: CardArtworkRepository,
    selectedCount: Int,
    limit: Int,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onCardToggle: (CardId) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.configure_quick_cards),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.quick_cards_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.quick_cards_count, selectedCount, limit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_cards)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(PICKER_COLUMN_COUNT),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = cards,
                    key = { card -> card.cardId.value },
                ) { card ->
                    QuickCardOptionButton(
                        card = card,
                        artworkRepository = artworkRepository,
                        onClick = { onCardToggle(card.cardId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCardOptionButton(
    card: QuickCardOptionUiState,
    artworkRepository: CardArtworkRepository,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = card.enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (card.selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardArtwork(
                cardId = card.cardId,
                displayName = card.displayName,
                artworkRepository = artworkRepository,
                size = 40.dp,
            )
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (card.selected) {
                        R.string.quick_selected_indicator
                    } else {
                        R.string.quick_add_indicator
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OpponentDeckHeader(state: TrackerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.opponent_deck),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.plays_recorded,
                    state.observationCount,
                    state.observationCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                R.string.deck_count,
                state.opponentDeck.size,
                state.deckCapacity,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyDeckMessage() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = stringResource(R.string.empty_deck),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OpponentDeckGrid(
    cards: List<TrackedCardUiState>,
    artworkRepository: CardArtworkRepository,
    onCardClick: (CardId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(DECK_COLUMN_COUNT).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowCards.forEach { card ->
                    TrackedCardButton(
                        card = card,
                        artworkRepository = artworkRepository,
                        onClick = { onCardClick(card.cardId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(DECK_COLUMN_COUNT - rowCards.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrackedCardButton(
    card: TrackedCardUiState,
    artworkRepository: CardArtworkRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (card.available) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (card.available) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 116.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardArtwork(
                cardId = card.cardId,
                displayName = card.displayName,
                artworkRepository = artworkRepository,
                size = 48.dp,
            )
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.cards_played_since,
                    card.cardsPlayedSince,
                    card.cardsPlayedSince,
                ),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (card.available) {
                    stringResource(R.string.available)
                } else {
                    pluralStringResource(
                        R.plurals.cards_away,
                        card.cardsUntilAvailable,
                        card.cardsUntilAvailable,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPickerSheet(
    cards: List<CardPickerItemUiState>,
    artworkRepository: CardArtworkRepository,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onCardClick: (CardId) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_observed_card),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.add_card_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_cards)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_matching_cards),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PICKER_COLUMN_COUNT),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = cards,
                        key = { card -> card.cardId.value },
                    ) { card ->
                        PickerCardButton(
                            card = card,
                            artworkRepository = artworkRepository,
                            onClick = { onCardClick(card.cardId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerCardButton(
    card: CardPickerItemUiState,
    artworkRepository: CardArtworkRepository,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CardArtwork(
                cardId = card.cardId,
                displayName = card.displayName,
                artworkRepository = artworkRepository,
                size = 48.dp,
            )
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardArtwork(
    cardId: CardId,
    displayName: String,
    artworkRepository: CardArtworkRepository,
    size: androidx.compose.ui.unit.Dp,
) {
    val artwork by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        cardId,
        artworkRepository,
    ) {
        value = withContext(Dispatchers.IO) {
            artworkRepository.bitmap(cardId)
        }
    }
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork == null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.fallbackLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            Image(
                bitmap = checkNotNull(artwork).asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FanContentDisclaimer() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.fan_content_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { uriHandler.openUri(FAN_CONTENT_POLICY_URL) }) {
            Text(stringResource(R.string.fan_content_policy))
        }
    }
}

private fun String.fallbackLabel(): String {
    val initials = trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .mapNotNull(String::firstOrNull)
        .joinToString("")
    return initials.take(2).uppercase().ifEmpty { "?" }
}

@Composable
private fun ResetConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_match_title)) },
        text = { Text(stringResource(R.string.reset_match_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.confirm_reset))
            }
        },
    )
}

private const val DECK_COLUMN_COUNT = 4
private const val PICKER_COLUMN_COUNT = 4
private const val FAN_CONTENT_POLICY_URL = "https://supercell.com/en/fan-content-policy/"
