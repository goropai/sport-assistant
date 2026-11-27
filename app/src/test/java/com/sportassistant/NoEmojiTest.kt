package com.sportassistant

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The UI uses vector icons, never emoji: emoji render differently on every device and clash with the
 * rest of the design. This keeps them from creeping back into strings or code.
 */
class NoEmojiTest {
    private fun isEmojiLike(cp: Int) =
        cp in 0x1F000..0x1FAFF || // pictographs, emoticons, transport, symbols
            cp in 0x2600..0x27BF || // misc symbols and dingbats (includes check marks, crosses, sparkles)
            cp in 0x2B00..0x2BFF || // misc symbols and arrows (stars, circles)
            cp in 0x2190..0x21FF || // arrows
            cp in 0x2300..0x23FF || // misc technical (watch, hourglass)
            cp == 0xFE0F || cp == 0x200D // emoji variation selector and joiner

    @Test
    fun `no emoji in sources or resources`() {
        val roots = listOf(File("src/main/java"), File("src/main/res"))
        assertTrue("run from the module directory", roots.all { it.exists() })

        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.flatMap { file ->
                file.readLines().withIndex().mapNotNull { (i, line) ->
                    val bad = line.codePoints().toArray().firstOrNull { isEmojiLike(it) }
                    bad?.let { "${file.path}:${i + 1} U+${it.toString(16).uppercase()}" }
                }
            }.toList()
        }
        assertTrue("emoji found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
