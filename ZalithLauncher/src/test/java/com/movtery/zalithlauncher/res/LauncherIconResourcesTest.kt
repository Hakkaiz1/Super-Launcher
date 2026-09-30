package com.movtery.zalithlauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Fixa a geometria do ícone: zona segura do adaptável, silhueta alpha-only
 * das notificações, densidades legadas e ausência dos assets antigos.
 */
class LauncherIconResourcesTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$relative não encontrado a partir de ${File("").absolutePath}")
    }

    private fun exists(relative: String): Boolean {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, relative).exists()) return true
            dir = dir.parentFile
        }
        return false
    }

    private fun read(relative: String): BufferedImage =
        ImageIO.read(repoFile(relative)) ?: error("não decodifiquei $relative")

    @Test
    fun sourceArtIsCommitted() {
        assertTrue("arte-fonte não está no repositório", exists("docs/assets/dbc-super-icon-1024.png"))
        val art = read("docs/assets/dbc-super-icon-1024.png")
        assertEquals(1024, art.width)
        assertEquals(1024, art.height)
    }

    @Test
    fun foregroundKeepsSafeZone() {
        val fg = read("ZalithLauncher/src/main/res/drawable-nodpi/ic_launcher_foreground.png")
        assertEquals(640, fg.width)
        assertEquals(640, fg.height)
        // arte ocupa 70% (448px) centrada → faixa livre de 96px em cada lado
        assertEquals(0, fg.getRGB(4, 4) ushr 24)
        assertEquals(0, fg.getRGB(635, 635) ushr 24)
        assertEquals(0, fg.getRGB(50, 320) ushr 24)
        assertTrue("centro do ícone ficou vazio", fg.getRGB(320, 320) ushr 24 > 0)
    }

    @Test
    fun monochromeIsAlphaOnlyWhite() {
        val mc = read("ZalithLauncher/src/main/res/drawable-nodpi/ic_launcher_monochrome.png")
        assertEquals(640, mc.width)
        assertEquals(640, mc.height)
        var opaque = 0
        for (y in 0 until mc.height step 4) {
            for (x in 0 until mc.width step 4) {
                val p = mc.getRGB(x, y)
                if (p ushr 24 > 0) {
                    opaque++
                    assertEquals(
                        "small icon precisa ser alpha-only",
                        0x00FFFFFF,
                        p and 0x00FFFFFF
                    )
                }
            }
        }
        assertTrue("silhueta monochrome vazia", opaque > 100)
    }

    @Test
    fun legacyDensityIconsAreGenerated() {
        val densities = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, size) in densities) {
            for (name in listOf("ic_launcher.png", "ic_launcher_round.png")) {
                val img = read("ZalithLauncher/src/main/res/mipmap-$density/$name")
                assertEquals("mipmap-$density/$name", size, img.width)
                assertEquals(size, img.height)
            }
        }
    }

    @Test
    fun oldIconAssetsAreGone() {
        for (density in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            for (name in listOf("ic_launcher.webp", "ic_launcher_round.webp")) {
                assertFalse(
                    "asset antigo ainda presente: $density/$name",
                    exists("ZalithLauncher/src/main/res/mipmap-$density/$name")
                )
            }
        }
        assertFalse(exists("ZalithLauncher/src/main/res/drawable/ic_launcher_foreground.xml"))
        assertFalse(exists("ZalithLauncher/src/main/res/drawable/ic_launcher_monochrome.xml"))
    }

    @Test
    fun backgroundColorIsValidHex() {
        val xml = repoFile("ZalithLauncher/src/main/res/values/ic_launcher_background.xml").readText()
        Regex("""#([0-9A-Fa-f]{6})""").find(xml)
            ?: error("nenhuma cor #RRGGBB em ic_launcher_background.xml")
    }

    @Test
    fun notificationsUseAlphaOnlyIcon() {
        val services = listOf(
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/keepalive/TaskKeepAliveService.kt",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameService.kt",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/terracotta/TerracottaVPNService.java",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/jvm_server/JvmService.kt"
        )
        for (file in services) {
            val text = repoFile(file).readText()
            assertTrue(
                "$file precisa usar o small icon alpha-only",
                text.contains("setSmallIcon(R.drawable.ic_launcher_monochrome)")
            )
        }
    }
}
