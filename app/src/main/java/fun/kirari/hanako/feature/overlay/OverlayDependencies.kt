package `fun`.kirari.hanako.feature.overlay

import android.content.Context
import android.content.Intent
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.hanako.solve.application.SolveOperations

internal data class OverlayDependencies(
    val settingsRepository: SettingsRepository,
    val solveOperations: SolveOperations,
    val providerModelsApi: ProviderModelsApi,
    val mainActivityIntent: (Context) -> Intent
)

internal interface OverlayDependenciesProvider {
    val overlayDependencies: OverlayDependencies
}
