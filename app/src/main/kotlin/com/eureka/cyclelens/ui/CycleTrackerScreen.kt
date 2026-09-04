package com.eureka.cyclelens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eureka.cyclelens.CardUiState
import com.eureka.cyclelens.CycleTrackerViewModel
import com.eureka.cyclelens.R
import com.eureka.cyclelens.TrackerUiState

@Composable
fun CycleTrackerRoute(
    trackerViewModel: CycleTrackerViewModel = viewModel(),
) {
    val state by trackerViewModel.uiState.collectAsStateWithLifecycle()

    CycleTrackerScreen(
        state = state,
        onInputChanged = trackerViewModel::onInputChanged,
        onObserve = trackerViewModel::observeInput,
        onUndo = trackerViewModel::undo,
        onReset = trackerViewModel::reset,
    )
}

@Composable
fun CycleTrackerScreen(
    state: TrackerUiState,
    onInputChanged: (String) -> Unit,
    onObserve: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.card_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onObserve() }),
            )

            Button(
                onClick = onObserve,
                enabled = state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.observe))
            }

            Text(
                text = stringResource(R.string.observations, state.observations),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.discovered_cards, state.cards.size),
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.cards.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_cards_observed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.cards.forEach { card ->
                    CardStatus(card)
                    HorizontalDivider()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.undo))
                }
                OutlinedButton(
                    onClick = onReset,
                    enabled = state.observations > 0 || state.input.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }
}

@Composable
private fun CardStatus(card: CardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = card.cardId.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(stringResource(R.string.played_since, card.cardsPlayedSince))
        Text(
            text = if (card.available) {
                stringResource(R.string.until_available_now)
            } else {
                stringResource(R.string.until_available, card.cardsUntilAvailable)
            },
            color = if (card.available) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (card.available) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
