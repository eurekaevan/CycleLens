package com.eureka.cyclelens.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.eureka.cyclelens.R
import com.eureka.cyclelens.catalog.CardArtworkRepository
import cyclelens.core.CardId
import kotlin.math.roundToInt

internal class OverlayViewFactory(
    private val context: Context,
    private val artworkRepository: CardArtworkRepository,
) {
    data class Callbacks(
        val onCardClick: (CardId) -> Unit,
        val onAddClick: () -> Unit,
        val onPickerCardClick: (CardId) -> Unit,
        val onUndoClick: () -> Unit,
        val onCollapseClick: () -> Unit,
        val onExpandClick: () -> Unit,
        val onClosePickerClick: () -> Unit,
    )

    data class Content(
        val view: View,
        val dragHandle: View,
        val onDragHandleTap: (() -> Unit)? = null,
    )

    fun create(
        state: OverlayPresentationState,
        callbacks: Callbacks,
    ): Content = when (state.panel) {
        OverlayPanel.EXPANDED -> createExpanded(state, callbacks)
        OverlayPanel.COLLAPSED -> createCollapsed(state, callbacks)
        OverlayPanel.PICKER -> createPicker(state, callbacks)
    }

    private fun createExpanded(
        state: OverlayPresentationState,
        callbacks: Callbacks,
    ): Content {
        val dimensions = dimensions(state.sizeMode)
        val panel = panel(state, dimensions)
        val header = header(
            title = context.getString(R.string.overlay_drag_title),
            actionText = "−",
            actionDescription = context.getString(R.string.overlay_collapse),
            onAction = callbacks.onCollapseClick,
            dimensions = dimensions,
        )
        panel.addView(header.view)

        val deck = verticalLayout().apply {
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        if (state.cards.isEmpty()) {
            deck.addView(
                TextView(context).apply {
                    text = context.getString(R.string.overlay_empty_deck)
                    setTextColor(SECONDARY_TEXT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                },
            )
        } else {
            repeat(DECK_ROWS) { rowIndex ->
                val cards = state.cards
                    .drop(rowIndex * DECK_COLUMNS)
                    .take(DECK_COLUMNS)
                deck.addView(cardRow(cards, callbacks.onCardClick, dimensions))
            }
        }
        panel.addView(deck)

        val footer = horizontalLayout().apply {
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        footer.addView(
            actionButton(
                text = context.getString(R.string.overlay_add),
                enabled = state.canAddCard,
                onClick = callbacks.onAddClick,
            ),
            weightedLayoutParams(height = dimensions.footerHeightDp, margin = dimensions.gapDp),
        )
        footer.addView(
            actionButton(
                text = context.getString(R.string.overlay_undo),
                enabled = state.canUndo,
                onClick = callbacks.onUndoClick,
            ),
            weightedLayoutParams(height = dimensions.footerHeightDp, margin = dimensions.gapDp),
        )
        panel.addView(footer)

        return Content(view = panel, dragHandle = header.dragHandle)
    }

    private fun createCollapsed(
        state: OverlayPresentationState,
        callbacks: Callbacks,
    ): Content {
        val dimensions = dimensions(state.sizeMode)
        val handle = actionButton(
            text = "CL",
            enabled = true,
            onClick = callbacks.onExpandClick,
        ).apply {
            contentDescription = context.getString(R.string.overlay_expand)
            textSize = 12f
            background = roundedBackground(
                colorWithPercent(COLLAPSED_COLOR, state.backgroundOpacity.percent),
                radiusDp = 12,
                strokeColor = BORDER_COLOR,
                strokeWidthDp = 1,
            )
            elevation = dp(4).toFloat()
        }
        val frame = FrameLayout(context).apply {
            addView(
                handle,
                FrameLayout.LayoutParams(
                    dp(dimensions.collapsedWidthDp),
                    dp(dimensions.collapsedHeightDp),
                ),
            )
        }
        return Content(
            view = frame,
            dragHandle = handle,
            onDragHandleTap = callbacks.onExpandClick,
        )
    }

    private fun createPicker(
        state: OverlayPresentationState,
        callbacks: Callbacks,
    ): Content {
        val dimensions = dimensions(state.sizeMode)
        val panel = panel(state, dimensions)
        val header = header(
            title = context.getString(R.string.overlay_picker_title),
            actionText = "×",
            actionDescription = context.getString(R.string.overlay_close_picker),
            onAction = callbacks.onClosePickerClick,
            dimensions = dimensions,
        )
        panel.addView(header.view)

        val grid = verticalLayout().apply {
            setPadding(dp(4), dp(2), dp(4), dp(6))
        }
        state.pickerCards.chunked(DECK_COLUMNS).forEach { cards ->
            val row = horizontalLayout()
            cards.forEach { card ->
                row.addView(
                    pickerCardButton(card, dimensions) {
                        callbacks.onPickerCardClick(card.cardId)
                    },
                    weightedLayoutParams(
                        height = dimensions.pickerCardHeightDp,
                        margin = dimensions.gapDp,
                    ),
                )
            }
            repeat(DECK_COLUMNS - cards.size) {
                row.addView(
                    Space(context),
                    weightedLayoutParams(
                        height = dimensions.pickerCardHeightDp,
                        margin = dimensions.gapDp,
                    ),
                )
            }
            grid.addView(row)
        }
        panel.addView(grid)

        return Content(view = panel, dragHandle = header.dragHandle)
    }

    private fun cardRow(
        cards: List<OverlayCardUiState>,
        onCardClick: (CardId) -> Unit,
        dimensions: OverlayDimensions,
    ): View = horizontalLayout().apply {
        cards.forEach { card ->
            addView(
                trackedCardButton(card, dimensions) { onCardClick(card.cardId) },
                weightedLayoutParams(
                    height = dimensions.cardHeightDp,
                    margin = dimensions.gapDp,
                ),
            )
        }
        repeat(DECK_COLUMNS - cards.size) {
            addView(
                Space(context),
                weightedLayoutParams(
                    height = dimensions.cardHeightDp,
                    margin = dimensions.gapDp,
                ),
            )
        }
    }

    private fun trackedCardButton(
        card: OverlayCardUiState,
        dimensions: OverlayDimensions,
        onClick: () -> Unit,
    ): View = FrameLayout(context).apply {
        contentDescription = context.getString(
            R.string.overlay_card_description,
            card.displayName,
            card.cycleLabel,
        )
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
        setOnClickListener { onClick() }
        background = roundedBackground(
            color = colorWithPercent(CARD_COLOR, CARD_BACKGROUND_PERCENT),
            radiusDp = 12,
            strokeColor = BORDER_COLOR,
            strokeWidthDp = 1,
        )
        val labelHeightDp = if (card.shortName == null) 0 else dimensions.cardLabelHeightDp
        addView(
            artworkView(card.cardId, card.displayName),
            FrameLayout.LayoutParams(
                dp(dimensions.cardArtworkSizeDp),
                dp(dimensions.cardArtworkSizeDp),
                Gravity.START or Gravity.CENTER_VERTICAL,
            ).apply {
                marginStart = dp(4)
                bottomMargin = dp(labelHeightDp)
            },
        )
        addView(
            TextView(context).apply {
                text = card.cycleLabel
                setTextColor(if (card.available) AVAILABLE_STATUS_COLOR else PRIMARY_TEXT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensions.cardTextSp)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
            },
            FrameLayout.LayoutParams(
                dp(dimensions.cycleLabelWidthDp),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ).apply {
                marginEnd = dp(3)
                bottomMargin = dp(labelHeightDp)
            },
        )
        card.shortName?.let { shortName ->
            addView(
                TextView(context).apply {
                    text = shortName
                    setTextColor(FULL_LABEL_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensions.fullLabelTextSp)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(2), 0, dp(2), 0)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(dimensions.cardLabelHeightDp),
                    Gravity.BOTTOM,
                ),
            )
        }
    }

    private fun pickerCardButton(
        card: OverlayPickerCardUiState,
        dimensions: OverlayDimensions,
        onClick: () -> Unit,
    ): View = FrameLayout(context).apply {
        contentDescription = context.getString(
            R.string.overlay_add_card_description,
            card.displayName,
        )
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
        setOnClickListener { onClick() }
        background = roundedBackground(
            color = colorWithPercent(CARD_COLOR, PICKER_BACKGROUND_PERCENT),
            radiusDp = 12,
            strokeColor = BORDER_COLOR,
            strokeWidthDp = 1,
        )
        addView(
            artworkView(card.cardId, card.displayName),
            FrameLayout.LayoutParams(
                dp(dimensions.pickerArtworkSizeDp),
                dp(dimensions.pickerArtworkSizeDp),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(2)
            },
        )
        addView(
            TextView(context).apply {
                text = card.shortName
                setTextColor(PRIMARY_TEXT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensions.pickerTextSp)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), 0, dp(2), 0)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(dimensions.pickerLabelHeightDp),
                Gravity.BOTTOM,
            ),
        )
    }

    private fun artworkView(cardId: CardId, displayName: String): View {
        val artwork = artworkRepository.bitmap(cardId)
        if (artwork != null) {
            return ImageView(context).apply {
                setImageBitmap(artwork)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        return TextView(context).apply {
            text = displayName.fallbackLabel()
            setTextColor(PRIMARY_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBackground(
                color = colorWithPercent(ACTION_COLOR, 65),
                radiusDp = 10,
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun actionButton(
        text: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView = baseButton(onClick).apply {
        this.text = text
        isEnabled = enabled
        setTextColor(if (enabled) PRIMARY_TEXT_COLOR else DISABLED_TEXT_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = roundedBackground(
            colorWithPercent(
                ACTION_COLOR,
                if (enabled) ACTION_BACKGROUND_PERCENT else DISABLED_BACKGROUND_PERCENT,
            ),
            radiusDp = 12,
            strokeColor = BORDER_COLOR,
            strokeWidthDp = 1,
        )
    }

    private fun baseButton(onClick: () -> Unit): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(PRIMARY_TEXT_COLOR)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(4), dp(4), dp(4))
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
        setOnClickListener { onClick() }
    }

    private fun header(
        title: String,
        actionText: String,
        actionDescription: String,
        onAction: () -> Unit,
        dimensions: OverlayDimensions,
    ): Header {
        val row = horizontalLayout().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val dragHandle = TextView(context).apply {
            text = title
            contentDescription = context.getString(R.string.overlay_drag)
            setTextColor(PRIMARY_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensions.headerTextSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            isClickable = true
        }
        row.addView(
            dragHandle,
            LinearLayout.LayoutParams(0, dp(dimensions.headerHeightDp), 1f),
        )
        row.addView(
            actionButton(actionText, enabled = true, onClick = onAction).apply {
                contentDescription = actionDescription
                textSize = 18f
            },
            LinearLayout.LayoutParams(
                dp(dimensions.headerHeightDp),
                dp(dimensions.headerHeightDp),
            ),
        )
        return Header(view = row, dragHandle = dragHandle)
    }

    private fun panel(
        state: OverlayPresentationState,
        dimensions: OverlayDimensions,
    ): LinearLayout = verticalLayout().apply {
        layoutParams = FrameLayout.LayoutParams(
            dp(dimensions.panelWidthDp),
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        background = roundedBackground(
            colorWithPercent(PANEL_COLOR, state.backgroundOpacity.percent),
            radiusDp = 14,
        )
        elevation = dp(4).toFloat()
    }

    private fun verticalLayout() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun horizontalLayout() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun weightedLayoutParams(
        height: Int,
        margin: Int,
    ) = LinearLayout.LayoutParams(0, dp(height), 1f).apply {
        setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 0,
    ): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null && strokeWidthDp > 0) {
            setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun colorWithPercent(
        color: Int,
        opacityPercent: Int,
    ): Int = Color.argb(
        (255 * opacityPercent / 100f).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun dimensions(sizeMode: OverlaySizeMode): OverlayDimensions = when (sizeMode) {
        OverlaySizeMode.COMPACT -> OverlayDimensions(
            panelWidthDp = 248,
            headerHeightDp = 34,
            cardHeightDp = 52,
            pickerCardHeightDp = 48,
            footerHeightDp = 36,
            collapsedWidthDp = 38,
            collapsedHeightDp = 32,
            gapDp = 1,
            headerTextSp = 12f,
            cardTextSp = 14f,
            pickerTextSp = 9f,
            fullLabelTextSp = 8f,
            cardArtworkSizeDp = 32,
            pickerArtworkSizeDp = 28,
            cycleLabelWidthDp = 24,
            cardLabelHeightDp = 14,
            pickerLabelHeightDp = 14,
        )
        OverlaySizeMode.COMFORTABLE -> OverlayDimensions(
            panelWidthDp = 304,
            headerHeightDp = 40,
            cardHeightDp = 68,
            pickerCardHeightDp = 58,
            footerHeightDp = 42,
            collapsedWidthDp = 44,
            collapsedHeightDp = 36,
            gapDp = 2,
            headerTextSp = 13f,
            cardTextSp = 16f,
            pickerTextSp = 10f,
            fullLabelTextSp = 9f,
            cardArtworkSizeDp = 42,
            pickerArtworkSizeDp = 36,
            cycleLabelWidthDp = 28,
            cardLabelHeightDp = 16,
            pickerLabelHeightDp = 16,
        )
    }

    private fun selectableForeground(): Drawable? {
        val value = TypedValue()
        val resolved = context.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            value,
            true,
        )
        return if (resolved && value.resourceId != 0) {
            context.getDrawable(value.resourceId)
        } else {
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun String.fallbackLabel(): String {
        val initials = trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .mapNotNull(String::firstOrNull)
            .joinToString("")
        return initials.take(2).uppercase().ifEmpty { "?" }
    }

    private data class Header(
        val view: View,
        val dragHandle: View,
    )

    private data class OverlayDimensions(
        val panelWidthDp: Int,
        val headerHeightDp: Int,
        val cardHeightDp: Int,
        val pickerCardHeightDp: Int,
        val footerHeightDp: Int,
        val collapsedWidthDp: Int,
        val collapsedHeightDp: Int,
        val gapDp: Int,
        val headerTextSp: Float,
        val cardTextSp: Float,
        val pickerTextSp: Float,
        val fullLabelTextSp: Float,
        val cardArtworkSizeDp: Int,
        val pickerArtworkSizeDp: Int,
        val cycleLabelWidthDp: Int,
        val cardLabelHeightDp: Int,
        val pickerLabelHeightDp: Int,
    )

    private companion object {
        const val DECK_COLUMNS = 4
        const val DECK_ROWS = 2
        const val CARD_BACKGROUND_PERCENT = 38
        const val PICKER_BACKGROUND_PERCENT = 44
        const val ACTION_BACKGROUND_PERCENT = 40
        const val DISABLED_BACKGROUND_PERCENT = 22
        val PANEL_COLOR: Int = Color.rgb(27, 30, 38)
        val CARD_COLOR: Int = Color.rgb(55, 63, 78)
        val ACTION_COLOR: Int = Color.rgb(75, 68, 136)
        val COLLAPSED_COLOR: Int = Color.rgb(75, 68, 136)
        val PRIMARY_TEXT_COLOR: Int = Color.WHITE
        val AVAILABLE_STATUS_COLOR: Int = Color.rgb(116, 255, 174)
        val FULL_LABEL_COLOR: Int = Color.argb(194, 255, 255, 255)
        val DISABLED_TEXT_COLOR: Int = Color.argb(140, 255, 255, 255)
        val BORDER_COLOR: Int = Color.argb(60, 255, 255, 255)
        val SECONDARY_TEXT_COLOR: Int = Color.rgb(210, 213, 222)
    }
}
