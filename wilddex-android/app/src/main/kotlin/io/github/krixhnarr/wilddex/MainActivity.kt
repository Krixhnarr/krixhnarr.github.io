package io.github.krixhnarr.wilddex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.data.SaveStore
import io.github.krixhnarr.wilddex.ui.WildDexRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: GameModel

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { it.write(model.state.toJson().toByteArray()) }
        model.say("BACKUP SAVED")
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@registerForActivityResult
            model.replace(PlayerState.fromJson(text))
            model.say("BINDER RESTORED")
        } catch (e: Exception) {
            model.say("THAT FILE ISN'T A WILDDEX BACKUP")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = GameModel(SaveStore(applicationContext))
        model.exportBackup = { exportLauncher.launch("wilddex-backup-${Game.today()}.json") }
        model.importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        model.applySky()
        if (!model.state.onboarded) model.open(Sheet.Intro)
        // re-check day/night every minute
        lifecycleScope.launch { while (true) { delay(60_000); model.applySky() } }
        setContent { WildDexRoot(model) }
    }
}
