package app.aurora

import kotlin.test.Test
import kotlin.test.assertEquals

class MiniPlayersTest {
    private fun rule(hasVideo: Boolean, videoEnabled: Boolean = true, mini: String? = null, closed: String? = null, hidden: String? = null) =
        miniPlayers("x", hasVideo, videoEnabled, mini, closed, hidden)

    @Test fun songShowsOnlyTheAudioMiniPlayer() = assertEquals(MiniPlayers(audio = true, video = false), rule(hasVideo = false))

    @Test fun videoShowsOnlyTheFloatingVideo() = assertEquals(MiniPlayers(audio = false, video = true), rule(hasVideo = true))

    @Test fun closingTheVideoWithXBringsBackTheAudioMiniPlayer() =
        assertEquals(MiniPlayers(audio = true, video = false), rule(hasVideo = true, closed = "x"))

    @Test fun audioOnlyVideoUsesTheAudioMiniPlayer() =
        assertEquals(MiniPlayers(audio = true, video = false), rule(hasVideo = true, videoEnabled = false))

    @Test fun swipingInPauseHidesBoth() {
        assertEquals(MiniPlayers(audio = false, video = false), rule(hasVideo = true, mini = "x", hidden = "x"))
        assertEquals(MiniPlayers(audio = false, video = false), rule(hasVideo = false, mini = "x", hidden = "x"))
    }

    @Test fun hiddenForAnotherTrackDoesNotApply() =
        assertEquals(MiniPlayers(audio = false, video = true), rule(hasVideo = true, mini = "y", closed = "y", hidden = "y"))

    @Test fun nothingPlaying() = assertEquals(MiniPlayers(audio = false, video = false), miniPlayers(null, false, true, null, null, null))
}
