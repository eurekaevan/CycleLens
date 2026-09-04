package cyclelens.core

class CycleTracker(
    private val rules: CycleRules = StandardCycleRules,
) {
    private val lastObservedAt = mutableMapOf<CardId, Int>()
    private var observationCount = 0

    fun observe(card: CardId) {
        observationCount += 1
        lastObservedAt[card] = observationCount
    }

    fun discoveredCards(): Set<CardId> = lastObservedAt.keys.toSet()

    fun cardsPlayedSince(card: CardId): Int? =
        lastObservedAt[card]?.let { lastObservation ->
            observationCount - lastObservation
        }

    fun cardsUntilAvailable(card: CardId): Int? =
        cardsPlayedSince(card)?.let { cardsPlayed ->
            (rules.cardsRequiredToCycle - cardsPlayed).coerceAtLeast(0)
        }

    fun reset() {
        lastObservedAt.clear()
        observationCount = 0
    }
}
