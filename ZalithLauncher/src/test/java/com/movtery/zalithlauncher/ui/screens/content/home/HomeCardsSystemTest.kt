package com.movtery.zalithlauncher.ui.screens.content.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeCardsSystemTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$relative não encontrado a partir de ${File("").absolutePath}")
    }

    @Test
    fun unofficialNoticeCardIsRegistered() {
        val ids = HomeCards.systemCards().map { it.id }
        assertTrue(
            "card do aviso ausente em $ids",
            "system_unofficial_notice" in ids
        )
    }

    @Test
    fun noticeCopyIsEnglishAndNotTranslatable() {
        val xml = repoFile("ZalithLauncher/src/main/res/values/strings.xml").readText()
        assertTrue(
            "o aviso precisa ser inglês e non-translatable, senão o Weblate traduz",
            xml.contains(
                """<string name="unofficial_modified_notice" translatable="false">""" +
                    """Unofficial Modified Version by Hakkaiz</string>"""
            )
        )
    }
}
