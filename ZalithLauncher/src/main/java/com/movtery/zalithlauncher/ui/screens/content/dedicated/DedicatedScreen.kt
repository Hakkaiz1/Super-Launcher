/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.dedicated

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.dedicated.DedicatedPackState
import com.movtery.zalithlauncher.game.dedicated.PackState
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.screens.content.elements.TitleTaskFlowDialog

/**
 * Tela única do launcher dedicado (spec §6): ícone do pack, nome, aviso de
 * versão modificada e um botão grande. Não há lista de versões nem navegação.
 */
@Composable
fun DedicatedScreen(
    onLaunchGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by DedicatedPackState.state.collectAsStateWithLifecycle()
    val tasks by DedicatedPackState.tasks.collectAsStateWithLifecycle()
    val logOutput by DedicatedPackState.logOutput.collectAsStateWithLifecycle()

    if (tasks.isNotEmpty()) {
        TitleTaskFlowDialog(
            title = stringResource(R.string.dedicated_installing_title),
            tasks = tasks,
            onCancel = { DedicatedPackState.cancelInstall() },
            logOutput = logOutput
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // R.mipmap.ic_launcher é um adaptive-icon (XML) e o painterResource() do
        // Compose só aceita VectorDrawable ou raster, então carregamos o ícone
        // instalado do app como bitmap.
        val launcherIcon = remember(context.packageManager, context.packageName) {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
        }
        Image(
            bitmap = launcherIcon,
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dedicated_pack_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        // Aviso obrigatório exibido incondicionalmente (R6 / §8)
        Text(
            text = stringResource(R.string.unofficial_modified_notice),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when (val current = state) {
            PackState.Checking, PackState.Installing -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            PackState.Ready -> {
                DedicatedButton(text = stringResource(R.string.dedicated_play), onClick = onLaunchGame)
            }
            PackState.NotInstalled -> {
                DedicatedButton(
                    text = stringResource(R.string.dedicated_install),
                    onClick = { DedicatedPackState.install(context) }
                )
            }
            is PackState.NeedsUpdate -> {
                Text(
                    text = "${current.installedVersion} → ${current.availableVersion}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_update),
                    onClick = { DedicatedPackState.install(context) }
                )
            }
            PackState.Offline -> {
                Text(
                    text = stringResource(R.string.dedicated_offline),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_retry),
                    onClick = { DedicatedPackState.launchCheck(context) }
                )
            }
            is PackState.Failed -> {
                AndroidStringText(
                    text = current.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_retry),
                    onClick = { DedicatedPackState.launchCheck(context) }
                )
            }
        }
    }
}

@Composable
private fun DedicatedButton(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            // O Button do Material 3 só aceita cor chapada; o gradiente vai no
            // container (mesmo shape, mesmo ripple por cima) e o botão fica
            // transparente. O rótulo usa onPrimary, que o DBCSuperThemeTest
            // garante contrastar com as DUAS pontas do gradiente.
            .background(
                brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
                shape = ButtonDefaults.shape
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onPrimary
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
