package com.movtery.zalithlauncher.components

import com.movtery.zalithlauncher.components.jre.Jre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Travas da build dedicada Super Launcher: o pack é travado em Minecraft 1.7.10,
 * que roda apenas em Java 8. Então o launcher não empacota, não lista nem
 * tenta instalar os runtimes maiores (17/21/25) — eles só inflariam o APK.
 */
class DedicatedRuntimesTest {

    private fun exists(relative: String): Boolean {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, relative).exists()) return true
            dir = dir.parentFile
        }
        return false
    }

    @Test
    fun onlyJava8IsBundled() {
        assertEquals(
            "só o Java 8 pode ser empacotado nesta build",
            listOf(8),
            Jre.entries.filter { it.bundled }.map { it.majorVersion }
        )
    }

    @Test
    fun retryNeverEscalatesToAnUnbundledRuntime() {
        assertNull(
            "Java 8 não pode escalar pra um runtime que não existe mais",
            Jre.JRE_8.nextRetry()
        )
        assertNull(Jre.JRE_17.nextRetry())
        assertNull(Jre.JRE_8.nextRetry()?.nextRetry())
    }

    @Test
    fun jre172125AssetsAreGone() {
        for (version in listOf(17, 21, 25)) {
            assertFalse(
                "assets/runtimes/jre-$version ainda está no repositório",
                exists("ZalithLauncher/src/main/assets/runtimes/jre-$version")
            )
        }
        assertTrue(
            "runtimes/jre-8 precisa continuar empacotado",
            exists("ZalithLauncher/src/main/assets/runtimes/jre-8/version")
        )
    }
}
