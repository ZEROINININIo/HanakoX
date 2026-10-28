package `fun`.kirari.hanako.app

import android.app.Application
import android.content.Context
import android.content.Intent
import `fun`.kirari.hanako.feature.overlay.OverlayDependencies
import `fun`.kirari.hanako.feature.overlay.OverlayDependenciesProvider
import `fun`.kirari.hanako.feature.overlay.window.AntiScreenshotHelper
import ru.noties.jlatexmath.JLatexMathAndroid

internal class HanakoApplication : Application(), OverlayDependenciesProvider {
    internal lateinit var container: AppContainer
        private set
    override val overlayDependencies: OverlayDependencies
        get() = OverlayDependencies(
            settingsRepository = container.settingsRepository,
            solveOperations = container.workflow.operations,
            providerModelsApi = container.providerModelsApi,
            mainActivityIntent = ::mainActivityIntent
        )

    override fun onCreate() {
        super.onCreate()
        AntiScreenshotHelper.initialize()
        JLatexMathAndroid.init(this)
        container = AppContainer(applicationContext)
        instance = this
    }

    private fun mainActivityIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java)
    }

    companion object {
        lateinit var instance: HanakoApplication
            private set
    }
}
