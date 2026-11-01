package app.sucareto.hidora

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.sucareto.hidora.profile.ProfileRegistry
import app.sucareto.hidora.ui.home.DeviceSelectScreen
import app.sucareto.hidora.ui.home.DeviceSelectUiState
import app.sucareto.hidora.ui.home.DeviceViewModel
import app.sucareto.hidora.ui.home.RootAccess
import app.sucareto.hidora.ui.home.toMessage
import app.sucareto.hidora.ui.settings.AdvancedSettingsScreen
import app.sucareto.hidora.ui.settings.AppSettingsScreen
import app.sucareto.hidora.ui.settings.EnvironmentScreen
import app.sucareto.hidora.ui.settings.ProfileSettingsScreen
import app.sucareto.hidora.ui.theme.AppTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val deviceViewModel: DeviceViewModel by viewModels()

    private sealed interface Screen {
        data object Home : Screen
        data object Device : Screen
        data class ProfileSettings(val profileId: String) : Screen
        data object AppSettings : Screen
        data object AdvancedSettings : Screen
        data object Environment : Screen
    }

    private var screen by mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val snackbarHostState = remember { SnackbarHostState() }
                        val context = LocalContext.current
                        LaunchedEffect(Unit) {
                            deviceViewModel.statusEvents.collectLatest { status ->
                                snackbarHostState.showSnackbar(status.toMessage(context))
                            }
                        }
                        val device = deviceViewModel.activeDevice
                        LaunchedEffect(device) { if (device == null && screen == Screen.Device) showScreen(Screen.Home) }
                        when (val current = screen) {
                            Screen.Device -> if (device != null) {
                                device.session.Content(onLeave = {
                                    showScreen(Screen.Home)
                                    deviceViewModel.onLeaveDevice()
                                })
                            }
                            is Screen.ProfileSettings -> ProfileRegistry.byId(current.profileId)?.let { profile ->
                                ProfileSettingsScreen(
                                    profile = profile,
                                    deviceExists = deviceViewModel.isDevicePresent(profile),
                                    onBack = { showScreen(Screen.Home) }
                                )
                            }
                            Screen.AppSettings -> AppSettingsScreen(
                                onOpenAdvanced = { showScreen(Screen.AdvancedSettings) },
                                onOpenEnvironment = { showScreen(Screen.Environment) },
                                onBack = { showScreen(Screen.Home) }
                            )
                            Screen.Environment -> EnvironmentScreen(
                                runChecks = deviceViewModel::checkEnvironment,
                                onBack = { showScreen(Screen.AppSettings) }
                            )
                            Screen.AdvancedSettings -> AdvancedSettingsScreen(
                                isDevicePresent = deviceViewModel::isDevicePresent,
                                onBack = { showScreen(Screen.AppSettings) }
                            )
                            Screen.Home -> DeviceSelectScreen(
                                state = DeviceSelectUiState(
                                    previewMode = deviceViewModel.rootAccessState == RootAccess.DENIED,
                                    rows = deviceViewModel.deviceRows(),
                                    selectedProfileId = deviceViewModel.selectedProfileIdState,
                                    actions = deviceViewModel.selectedActions(onEntered = { showScreen(Screen.Device) })
                                ),
                                onSelect = deviceViewModel::onSelect,
                                onOpenSettings = { showScreen(Screen.ProfileSettings(it)) },
                                onOpenAppSettings = { showScreen(Screen.AppSettings) }
                            )
                        }
                        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        deviceViewModel.releaseInputs()
    }

    override fun onResume() {
        super.onResume()
        showScreen(screen)
    }

    private fun showScreen(next: Screen) {
        screen = next
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (next == Screen.Device && deviceViewModel.activeDevice?.profile?.immersiveLandscape == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
