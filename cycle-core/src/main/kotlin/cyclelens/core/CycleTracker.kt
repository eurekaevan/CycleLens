package cyclelens.core

class CycleTracker(
    private val rules: CycleRules = StandardCycleRules,
) {
    private val observationHistory = mutableListOf<CardId>()

    fun observe(card: CardId) {
        observationHistory.add(card)
    }

    fun undoLast(): CardId? = observationHistory.removeLastOrNull()

    fun observationCount(): Int = observationHistory.size

    fun discoveredCards(): Set<CardId> = observationHistory.toSet()

    fun cardsPlayedSince(card: CardId): Int? {
        val lastObservationIndex = observationHistory.lastIndexOf(card)
        return if (lastObservationIndex < 0) {
            null
        } else {
            observationHistory.lastIndex - lastObservationIndex
        }
    }

    fun cardsUntilAvailable(card: CardId): Int? =
        cardsPlayedSince(card)?.let { cardsPlayed ->
            (rules.cardsRequiredToCycle - cardsPlayed).coerceAtLeast(0)
        }

    fun reset() {
        observationHistory.clear()
    }
}
