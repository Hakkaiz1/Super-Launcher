package com.movtery.zalithlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fixa a identidade da build dedicada.
 *
 * Os valores moram em gradle.properties; ler o arquivo direto mantém o teste
 * independente da ofuscação de string do BuildKeys (que, com
 * isReturnDefaultValues=true, devolve null silencioso no JVM de teste).
 */
class BuildBrandTest {

    private fun findPropertiesFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle.properties")
            if (candidate.isFile && candidate.readText().contains("launcher_name=")) {
                return candidate
            }
            dir = dir.parentFile
        }
        error("gradle.properties com launcher_name não encontrado a partir de ${File("").absolutePath}")
    }

    private fun prop(name: String): String =
        findPropertiesFile().readLines()
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.trim()
            ?: error("propriedade $name não encontrada")

    @Test
    fun launcherNameIsSuperLauncher() {
        val name = prop("launcher_name")
        assertEquals("SuperLauncher", name)
        assertFalse("launcher_name não pode conter ZalithLauncher", name.contains("ZalithLauncher"))
        assertFalse("launcher_name não pode conter ZL", name.contains("ZL"))
    }

    @Test
    fun appNamesAreSuperLauncher() {
        assertEquals("Super Launcher", prop("launcher_app_name"))
        assertEquals("SL", prop("launcher_short_name"))
    }

    @Test
    fun homeUrlPointsAtModifiedSource() {
        // GPLv3 §6: a oferta de fonte deve ser da versão modificada
        assertEquals("https://github.com/Douglas2231/Super-Launcher", prop("url_home"))
    }

    @Test
    fun versionCodeExceedsUpstream() {
        // 200043 é o versionCode do upstream; abaixo disso o Android recusa a instalação
        assertTrue(
            "versionCode ${prop("launcher_version_code")} deve superar 200043",
            prop("launcher_version_code").toInt() > 200043
        )
    }

    @Test
    fun dedicatedBuildKeysAreDeclared() {
        assertTrue(BuildKeys.DEDICATED_MODE)
        assertEquals("dbc-super-oficial", BuildKeys.DEDICATED_PACK_SLUG)
        assertEquals("Minecraft Server", BuildKeys.DEDICATED_SERVER_NAME)
        assertEquals("dbcsuper.com", BuildKeys.DEDICATED_SERVER_IP)
    }
}
