package `fun`.kirari.hanako

import `fun`.kirari.hanako.core.ui.richtext.parseMarkdown
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun parseMarkdown_extractsCopyMarkerEmbeddedInSentence() {
        val result = parseMarkdown("**答案**：定义派生类构造函数时，通过[copy:初始化列表]调用基类构造函数。")

        assertEquals(
            "**答案**：定义派生类构造函数时，通过HanakoCopyMarker0调用基类构造函数。",
            result.preprocessed
        )
        assertEquals(1, result.copyMarkers.size)
        assertEquals("HanakoCopyMarker0", result.copyMarkers.single().placeholder)
        assertEquals("初始化列表", result.copyMarkers.single().copyText)
    }
}
