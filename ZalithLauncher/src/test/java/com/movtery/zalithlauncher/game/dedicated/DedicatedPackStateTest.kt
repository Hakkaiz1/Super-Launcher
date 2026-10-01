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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.dedicated

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.engine.AllSourcesFailedException
import com.movtery.zalithlauncher.game.download.modpack.technic.InsufficientSpaceException
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicApi
import com.movtery.zalithlauncher.ui.androidText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class DedicatedPackStateTest {

    private val installed = PackManifest("dbc-super-oficial", "10.8", emptyList())

    @Test
    fun `check flow goes not installed to installing to ready`() {
        var state: PackState = PackState.Checking

        state = DedicatedPackState.reduce(state, DedicatedEvent.CheckCompleted(installed = null, versionJsonPresent = true, apiIdentity = "10.8"))
        assertEquals(PackState.NotInstalled, state)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallRequested)
        assertEquals(PackState.Installing, state)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallSucceeded)
        assertEquals(PackState.Ready, state)
    }

    @Test
    fun `failed can go back to installing`() {
        var state: PackState = PackState.Checking
        state = DedicatedPackState.reduce(state, DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_generic)))
        assertTrue(state is PackState.Failed)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallRequested)
        assertEquals(PackState.Installing, state)
    }

    @Test
    fun `double install request is ignored`() {
        val installing: PackState = PackState.Installing
        assertSame(installing, DedicatedPackState.reduce(installing, DedicatedEvent.InstallRequested))
    }

    @Test
    fun `matching identity is ready`() {
        assertEquals(PackState.Ready, DedicatedPackState.classifyCheck(installed, true, "10.8"))
    }

    @Test
    fun `different identity requests an update`() {
        val state = DedicatedPackState.classifyCheck(installed, true, "10.9")
        assertTrue(state is PackState.NeedsUpdate)
        state as PackState.NeedsUpdate
        assertEquals("10.8", state.installedVersion)
        assertEquals("10.9", state.availableVersion)
    }

    @Test
    fun `empty api identity stays ready`() {
        // spec §5.1: dois lados vazios ⇒ READY com aviso, nunca loop de update
        assertEquals(PackState.Ready, DedicatedPackState.classifyCheck(installed, true, null))
    }

    @Test
    fun `deleted version json is not installed`() {
        // Review Focus 4: manifesto existe mas o json da versão sumiu ⇒ instalar de novo
        assertEquals(PackState.NotInstalled, DedicatedPackState.classifyCheck(installed, false, "10.8"))
    }

    @Test
    fun `offline and failed events land in the right state`() {
        assertEquals(PackState.Offline,
            DedicatedPackState.reduce(PackState.Checking, DedicatedEvent.CheckOffline))
        val failed = DedicatedPackState.reduce(PackState.Checking,
            DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_api_build)))
        assertTrue(failed is PackState.Failed)
    }

    @Test
    fun `errors are classified distinctly`() {
        // Review Focus 5: 401 nunca vira offline; offline nunca some
        assertEquals(ErrorKind.API_BUILD_REJECTED,
            DedicatedPackState.classifyError(TechnicApi.BuildRejectedException()))
        assertEquals(ErrorKind.OFFLINE,
            DedicatedPackState.classifyError(UnknownHostException("api.technicpack.net")))
        assertEquals(ErrorKind.OFFLINE,
            DedicatedPackState.classifyError(
                AllSourcesFailedException("https://dropbox/x.zip", UnknownHostException("dropbox.com"))))
        assertEquals(ErrorKind.INSUFFICIENT_SPACE,
            DedicatedPackState.classifyError(InsufficientSpaceException("need 200 MB")))
        assertEquals(ErrorKind.GENERIC,
            DedicatedPackState.classifyError(IOException("disk exploded")))
    }

    @Test
    fun `identity falls back to url then to nothing`() {
        assertEquals("10.8", PackManifest.identityOf("10.8", "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf(null, "https://dl/x.zip"))
        assertNull(PackManifest.identityOf(null, null))
    }

    @Test
    fun `initial state is checking`() {
        // Asserção de sanidade do fluxo de abertura: nunca READY sem passar pelo check
        assertSame(PackState.Checking, DedicatedPackState.state.value)
    }
}
