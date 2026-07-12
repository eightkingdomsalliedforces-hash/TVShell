package dev.tvshell.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeMediaTest {
    @Test
    fun bilibiliPopularResponseBecomesRemoteFriendlyCards() {
        val json = """{"data":{"list":[{"aid":42,"title":"葬送的芙莉蓮","pic":"//i0.hdslb.com/a.jpg","owner":{"name":"UP主"},"bvid":"BV123"}]}}"""
        val cards = NativeMediaParser.bilibili(json)
        assertEquals("葬送的芙莉蓮", cards.single().title)
        assertEquals("https://www.bilibili.com/video/BV123", cards.single().playbackURL)
        assertEquals("https://i0.hdslb.com/a.jpg", cards.single().thumbnailURL)
    }

    @Test
    fun youtubeInitialDataBecomesNativeCardsWithoutOpeningAWebList() {
        val html = """{"videoRenderer":{"videoId":"abc123","thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/abc123/hqdefault.jpg"}]},"title":{"runs":[{"text":"官方動畫"}]},"ownerText":{"runs":[{"text":"官方頻道"}]}}}"""
        val cards = NativeMediaParser.youtube(html)
        assertEquals("官方動畫", cards.single().title)
        assertEquals("https://www.youtube.com/watch?v=abc123", cards.single().playbackURL)
    }

    @Test
    fun mediaScreenFocusClampsAndReturnsToTabs() {
        var state = NativeMediaState(cardCount = 3)
        state = state.reduce(RemoteCommand.Down).reduce(RemoteCommand.Right)
        assertEquals(1, state.focusedCard)
        state = state.reduce(RemoteCommand.Up)
        assertEquals(true, state.isTopNavigationFocused)
    }
}
