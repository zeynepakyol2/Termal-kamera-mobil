package edu.ibu.termotabip

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.ibu.termotabip.auth.LocalAuthManager
import edu.ibu.termotabip.auth.User
import edu.ibu.termotabip.ui.screens.*
import edu.ibu.termotabip.ui.theme.TermoTabipTheme
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel

private enum class Screen {
    SPLASH, ONBOARDING, AUTH,
    HOME, GALLERY, THERMAL, RESULT, HISTORY
}

// SharedPreferences key — onboarding gösterildi mi?
private const val PREF_ONBOARDING = "onboarding_done"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            println("HATA: OpenCV başlatılamadı!")
        } else {
            println("BAŞARILI: OpenCV emrinize amade!")
        }
        enableEdgeToEdge()
        setContent {
            TermoTabipTheme {
                TermoInjuryApp()
            }
        }
    }
}

@Composable
private fun TermoInjuryApp() {
    val context = LocalContext.current
    val vm: WoundAnalysisViewModel = viewModel()

    // Onboarding daha önce gösterildi mi?
    val onboardingDone = remember {
        context.getSharedPreferences("termotabip_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_ONBOARDING, false)
    }

    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    var currentUser   by remember { mutableStateOf<User?>(null) }

    // Splash bitti → nereye git?
    fun afterSplash() {
        currentScreen = when {
            !onboardingDone                      -> Screen.ONBOARDING
            LocalAuthManager.isLoggedIn(context) -> Screen.HOME.also {
                currentUser = LocalAuthManager.currentUser(context)
            }
            else                                 -> Screen.AUTH
        }
    }

    // Onboarding bitti → kaydet ve auth'a git
    fun afterOnboarding() {
        context.getSharedPreferences("termotabip_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_ONBOARDING, true).apply()
        currentScreen = if (LocalAuthManager.isLoggedIn(context)) {
            currentUser = LocalAuthManager.currentUser(context)
            Screen.HOME
        } else Screen.AUTH
    }

    when (currentScreen) {
        Screen.SPLASH -> SplashScreen(onFinished = { afterSplash() })

        Screen.ONBOARDING -> OnboardingScreen(onFinished = { afterOnboarding() })

        Screen.AUTH -> AuthScreen(
            onLoginSuccess = { user ->
                currentUser = user
                currentScreen = Screen.HOME
            }
        )

        else -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentScreen) {

                Screen.HOME -> HomeScreen(
                    viewModel            = vm,
                    currentUser          = currentUser,
                    onGalleryClick       = { vm.resetState(); currentScreen = Screen.GALLERY },
                    onThermalCameraClick = { vm.resetState(); currentScreen = Screen.THERMAL },
                    onHistoryClick       = { currentScreen = Screen.HISTORY },
                    onLogout = {
                        LocalAuthManager.logout(context)
                        currentUser = null
                        currentScreen = Screen.AUTH
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                Screen.GALLERY -> GalleryScreen(
                    viewModel      = vm,
                    onAnalysisDone = { currentScreen = Screen.RESULT },
                    onBack         = { currentScreen = Screen.HOME },
                    modifier       = Modifier.padding(innerPadding)
                )

                Screen.THERMAL -> ThermalCameraScreen(
                    viewModel      = vm,
                    onAnalysisDone = { currentScreen = Screen.RESULT },
                    onBack         = { currentScreen = Screen.HOME },
                    modifier       = Modifier.padding(innerPadding)
                )

                Screen.RESULT -> ResultScreen(
                    viewModel     = vm,
                    currentUser   = currentUser,
                    onNewAnalysis = { currentScreen = Screen.HOME },
                    modifier      = Modifier.padding(innerPadding)
                )

                Screen.HISTORY -> HistoryScreen(
                    currentUser = currentUser,
                    onBack      = { currentScreen = Screen.HOME },
                    modifier    = Modifier.padding(innerPadding)
                )

                else -> Unit
            }
        }
    }
}