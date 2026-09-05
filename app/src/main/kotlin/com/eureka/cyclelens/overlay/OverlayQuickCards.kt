package com.eureka.cyclelens.overlay

import com.eureka.cyclelens.catalog.CardCatalog
import cyclelens.core.CardId
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayQuickCards(
    private val catalog: CardCatalog,
    val limit: Int = DEFAULT_LIMIT,
    initialCardIds: Iterable<CardId>? = null,
) {
    private val mutableCardIds: MutableStateFlow<List<CardId>>
    val cardIds: StateFlow<List<CardId>>
        get() = mutableCardIds.asStateFlow()

    init {
        require(limit > 0) { "Quick card limit must be positive" }
        val initialSelection = initialCardIds
            ?: DEFAULT_CARD_IDS
                .asSequence()
                .filter(catalog::isCycleEligible)
                .take(limit)
                .toList()
        mutableCardIds = MutableStateFlow(validatedSnapshot(initialSelection))
    }

    @Synchronized
    fun setSelected(cardId: CardId, selected: Boolean): Boolean {
        if (!catalog.isCycleEligible(cardId)) {
            return false
        }

        val current = mutableCardIds.value
        if (selected) {
            if (cardId in current) {
                return true
            }
            if (current.size >= limit) {
                return false
            }
            mutableCardIds.value = immutableCopy(current + cardId)
        } else if (cardId in current) {
            mutableCardIds.value = immutableCopy(current - cardId)
        }
        return true
    }

    @Synchronized
    fun replace(cardIds: Iterable<CardId>) {
        mutableCardIds.value = validatedSnapshot(cardIds)
    }

    private fun validatedSnapshot(cardIds: Iterable<CardId>): List<CardId> {
        val uniqueCardIds = cardIds.toList().distinct()
        require(uniqueCardIds.size <= limit) {
            "Quick cards cannot contain more than $limit cards"
        }
        require(uniqueCardIds.all(catalog::isCycleEligible)) {
            "Quick cards must be cycle-eligible cards in the card catalog"
        }
        return immutableCopy(uniqueCardIds)
    }

    private fun immutableCopy(cardIds: List<CardId>): List<CardId> =
        Collections.unmodifiableList(cardIds.toList())

    companion object {
        const val DEFAULT_LIMIT = 16
        private val DEFAULT_CARD_IDS = listOf(
            "hog_rider",
            "fireball",
            "the_log",
            "knight",
            "skeletons",
            "ice_spirit",
            "cannon",
            "musketeer",
            "archers",
            "arrows",
            "zap",
            "tesla",
            "valkyrie",
            "mini_pekka",
            "goblin_barrel",
            "princess",
        ).map(::CardId)
    }
}
