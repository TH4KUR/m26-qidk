package com.megathon.incident.ui

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.megathon.incident.ui.i18n.LocalLanguage
import com.megathon.incident.ui.i18n.AppLanguage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megathon.incident.session.SessionState
import com.megathon.incident.ui.components.ProofSheet
import com.megathon.incident.ui.screens.CheckScreen
import com.megathon.incident.ui.screens.HomeScreen
import com.megathon.incident.ui.screens.ReportsScreen
import com.megathon.incident.ui.screens.SavedReportScreen
import com.megathon.incident.ui.screens.ReportScreen
import com.megathon.incident.ui.screens.SentScreen
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import com.megathon.incident.ui.theme.Theme

@Composable
fun AppRoot(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val view = LocalView.current
    val reduce = LocalReduceMotion.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onMicPermissionResult(it) }
    LaunchedEffect(ui.needsMicPermission) { if (ui.needsMicPermission) permission.launch(Manifest.permission.RECORD_AUDIO) }

    // Haptics only where they carry meaning: recording started, recording stopped, sent, and a problem.
    LaunchedEffect(ui.listening) { view.performHapticFeedback(if (ui.listening) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.GESTURE_END) }
    LaunchedEffect(ui.session.state) {
        when (ui.session.state) {
            is SessionState.Submitted -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            is SessionState.Failed -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            else -> Unit
        }
    }

    BackHandler(enabled = ui.showProof) { vm.closeProof() }
    BackHandler(enabled = ui.screen == Screen.CHECK && !ui.showProof) { vm.backToReport() }
    BackHandler(enabled = ui.screen == Screen.SAVED && !ui.showProof) { vm.closeSavedReport() }
    BackHandler(enabled = ui.screen == Screen.REPORTS && !ui.showProof) { vm.closeReports() }

    // The whole app follows the Hindi switch: one language for every screen, changed at once.
    val language = if (ui.hindiLane) AppLanguage.HI else AppLanguage.EN
    CompositionLocalProvider(LocalLanguage provides language) {
    Surface(Modifier.fillMaxSize().imePadding(), color = Theme.colors.canvas) {
        AnimatedContent(
            targetState = ui.screen,
            transitionSpec = {
                if (reduce) {
                    ContentTransform(EnterTransition.None, ExitTransition.None)
                } else {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(Motion.standard, delayMillis = Motion.quick / 2, easing = Motion.enter)) +
                        slideInHorizontally(tween(Motion.slow, easing = Motion.enter)) { dir * it / 14 }) togetherWith
                        (fadeOut(tween(Motion.quick, easing = Motion.exit)) +
                            slideOutHorizontally(tween(Motion.quick, easing = Motion.exit)) { -dir * it / 14 })
                }
            },
            label = "screen",
        ) { screen ->
            var lastUi by remember(screen) { mutableStateOf(ui) }
            if (screen == ui.screen) SideEffect { lastUi = ui }
            val screenUi = if (screen == ui.screen) ui else lastUi
            when (screen) {
                Screen.HOME -> HomeScreen(screenUi, vm)
                Screen.REPORT -> ReportScreen(screenUi, vm)
                Screen.CHECK -> CheckScreen(screenUi, vm)
                Screen.SENT -> SentScreen(screenUi, vm)
                Screen.REPORTS -> ReportsScreen(screenUi, vm)
                Screen.SAVED -> SavedReportScreen(screenUi, vm)
            }
        }
        if (ui.showProof) ProofSheet(ui, onClose = vm::closeProof)
    }
    }
}
