package `fun`.kirari.hanako.core.ui.richtext

import `fun`.kirari.hanako.core.model.RichTextBlockKind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextBlockTest {
    @Test
    fun extractsParagraphAndDisplayMathBlocksInOrder() {
        val blocks = extractRichTextBlocks("第一段\n\n$$\nx^2\n$$\n\n第二段")

        assertEquals(3, blocks.size)
        assertEquals(RichTextBlockKind.PARAGRAPH, blocks[0].kind)
        assertEquals(RichTextBlockKind.DISPLAY_MATH, blocks[1].kind)
        assertEquals(RichTextBlockKind.PARAGRAPH, blocks[2].kind)
        assertTrue(blocks[1].rawMarkdown.contains("x^2"))
    }
}
