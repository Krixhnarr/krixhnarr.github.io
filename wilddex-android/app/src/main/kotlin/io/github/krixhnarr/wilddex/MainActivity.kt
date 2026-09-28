package io.github.krixhnarr.wilddex

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import io.github.krixhnarr.wilddex.audio.GameFx
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.data.SaveStore
import io.github.krixhnarr.wilddex.ui.WildDexRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: GameModel
    private lateinit var fx: GameFx

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

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        model.update { settings.location = ok }
        model.say(if (ok) "LOCATION TAGS ON" else "LOCATION PERMISSION NEEDED")
    }

    /** Saves a rough (2-decimal ≈ 1 km) position for a capture, without delaying the reveal. */
    @SuppressLint("MissingPermission")
    private fun tagLocation(key: String) {
        if (!model.state.settings.location) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val save = { loc: Location? ->
            val rec = model.state.caught[key]
            if (loc != null && rec != null) {
                val p = listOf(Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0)
                if (rec.loc == null) rec.loc = p
                rec.lastLoc = p
                model.commit()
            }
        }
        val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.PASSIVE_PROVIDER
        val recent = lm.getLastKnownLocation(provider)
        if (recent != null && System.currentTimeMillis() - recent.time < 10 * 60_000) { save(recent); return }
        if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(provider, null, mainExecutor) { save(it ?: recent) }
        else save(recent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = GameModel(SaveStore(applicationContext))
        fx = GameFx(applicationContext) { model.state.settings }
        model.fx = fx
        model.exportBackup = { exportLauncher.launch("wilddex-backup-${Game.today()}.json") }
        model.importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        model.tagLocation = ::tagLocation
        model.enableLocation = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                model.update { settings.location = true }
                model.say("LOCATION TAGS ON")
            } else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        model.applySky()
        fx.sound.setScene(if (model.night) "night" else "day")
        if (!model.state.onboarded) model.open(Sheet.Intro)
        // re-check day/night every minute
        lifecycleScope.launch { while (true) { delay(60_000); model.applySky() } }
        setContent { WildDexRoot(model) }
    }

    override fun onStart() { super.onStart(); fx.sound.resume() }
    override fun onStop() { super.onStop(); fx.sound.pause(); fx.stopSpeaking() }
    override fun onDestroy() { super.onDestroy(); if (isFinishing) fx.shutdown() }
}
