package com.eureka.cyclelens

import android.app.Application
import com.eureka.cyclelens.catalog.AssetCardCatalogLoader
import com.eureka.cyclelens.catalog.CardArtworkRepository
import com.eureka.cyclelens.catalog.CardCatalog
import com.eureka.cyclelens.capture.CaptureSessionStateStore
import com.eureka.cyclelens.overlay.OverlayConfiguration
import com.eureka.cyclelens.overlay.OverlayQuickCards
import com.eureka.cyclelens.session.MatchSession

class CycleLensApplication : Application() {
    val matchSession: MatchSession = MatchSession()
    val captureSessionState: CaptureSessionStateStore = CaptureSessionStateStore()
    lateinit var cardCatalog: CardCatalog
        private set
    lateinit var cardArtworkRepository: CardArtworkRepository
        private set
    lateinit var overlayQuickCards: OverlayQuickCards
        private set
    val overlayConfiguration: OverlayConfiguration = OverlayConfiguration()

    override fun onCreate() {
        super.onCreate()
        cardCatalog = AssetCardCatalogLoader.load(this)
        cardArtworkRepository = CardArtworkRepository(assets, cardCatalog)
        overlayQuickCards = OverlayQuickCards(cardCatalog)
    }
}
