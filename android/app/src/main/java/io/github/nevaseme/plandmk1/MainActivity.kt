package io.github.nevaseme.plandmk1

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.github.nevaseme.plandmk1.ui.PlanApp
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import io.github.nevaseme.plandmk1.sharing.SharingService

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[PlanViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlanDMk1Theme { PlanApp(model) } }
        handleOpen(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleOpen(intent) }
    private fun handleOpen(intent: Intent) {
        if (intent.getBooleanExtra(SharingService.OPEN_SAFETY, false)) {
            model.openSafety.value = true; intent.removeExtra(SharingService.OPEN_SAFETY)
        }
    }
    override fun onStop() { model.maps.pause(); super.onStop() }
}
