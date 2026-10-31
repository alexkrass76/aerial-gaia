package app.orchard.tv.channels

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class YaosChannelsTest {
    @Test fun ignoresMalformedMetadataAndInvalidGroups() {
        assertTrue(YaosChannels.categories("broken").isEmpty())
        val groups = YaosChannels.categories("""{"custom":{"categories":[{"id":"music","name":"Music","number":3},{"id":"music","name":"Duplicate"},{"id":"","name":"Invalid"}]}}""")
        assertEquals(listOf(YaosChannels.Group("music", "Music", 3)), groups)
    }
    @Test fun artworkAcceptsOnlyHttpsAndUsesLogoBeforeThumbnail() {
        assertNull(YaosChannels.artwork("broken"))
        assertNull(YaosChannels.artwork("""{"custom":{"icon_url":"file:///private/file"}}"""))
        assertNull(YaosChannels.artwork("""{"custom":{"icon_url":"https://user:pass@example.com/icon"}}"""))
        assertEquals("https://example.com/logo", YaosChannels.artwork("""{"custom":{"logo_with_bg_url":"https://example.com/logo","channel_thumbnail":"https://example.com/thumb"}}"""))
    }
    @Test fun playbackKeepsTypedChannelUriAndCategoryInsideStockPlayer() {
        val c = YaosChannels.Channel(42, "com.yandex.tv.input.efir/service", "Test", "1", "stable", emptyList())
        val i = YaosChannels.playback(c, "music")
        assertEquals("com.yandex.tv.live", i.component?.packageName)
        assertEquals("music", i.getStringExtra("current_category_id"))
        assertEquals(Uri.parse("content://android.media.tv/channel/42"), i.getParcelableExtra<Uri>("channel_uri"))
        assertEquals(c.input, i.getStringExtra("input_id"))
        assertEquals(c.key, c.copy(id = 999).key)
    }
}
