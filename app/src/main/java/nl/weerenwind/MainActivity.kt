package nl.weerenwind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.weerenwind.ui.AppRoot
import nl.weerenwind.ui.Themes
import nl.weerenwind.ui.WeerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            val colors = Themes.byId(state.settings.theme, isSystemInDarkTheme())
            SideEffect {
                val ctl = WindowCompat.getInsetsController(window, window.decorView)
                ctl.isAppearanceLightStatusBars = colors.isLight
                ctl.isAppearanceLightNavigationBars = colors.isLight
                ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (state.settings.showStatusBar) ctl.show(WindowInsetsCompat.Type.statusBars())
                else ctl.hide(WindowInsetsCompat.Type.statusBars())
                if (state.settings.showNavBar) ctl.show(WindowInsetsCompat.Type.navigationBars())
                else ctl.hide(WindowInsetsCompat.Type.navigationBars())
            }
            WeerTheme(colors) { AppRoot(vm, state) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}
