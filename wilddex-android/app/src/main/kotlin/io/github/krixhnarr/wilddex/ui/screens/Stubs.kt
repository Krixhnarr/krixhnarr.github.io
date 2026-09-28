package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.core.CaptureResult
import io.github.krixhnarr.wilddex.core.Recognition

@Composable fun ScanScreen(model: GameModel) { Column(Modifier.fillMaxSize().padding(16.dp)) { ScreenTitle("Scan") } }
@Composable fun ArenaScreen(model: GameModel) { Column(Modifier.fillMaxSize().padding(16.dp)) { ScreenTitle("Arena") } }
@Composable fun RevealSheet(model: GameModel, r: CaptureResult) {}
@Composable fun ChoicesSheet(model: GameModel, options: List<Recognition.Candidate>, text: String) {}
@Composable fun SquadSheet(model: GameModel, slot: Int) {}
@Composable fun FightSheet(model: GameModel, kind: String) {}
