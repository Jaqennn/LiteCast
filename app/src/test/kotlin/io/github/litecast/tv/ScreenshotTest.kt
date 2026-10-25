/*
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.litecast.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.jqssun.airplay.audio.TrackInfo
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.service.VideoPlaybackInfo
import io.github.litecast.dlna.DlnaRenderer
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the TV screens at 960x540dp (1080p TV at xhdpi) for review and for the README.
 * Opt-in: ./gradlew testDebugUnitTest -Pscreenshots   (writes PNGs to docs/screenshots)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w960dp-h540dp-land-television-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun optIn() = Assume.assumeTrue(System.getProperty("litecast.screenshots") == "true")

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { LiteCastTheme { content() } }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private val readyHome = HomeUi(
        name = "客厅电视 · 轻投",
        airplay = AirPlayService.ServerState.RUNNING,
        dlna = DlnaRenderer.Status.READY,
        dlnaEnabled = true,
        ip = "192.168.1.20",
        canPopUp = true,
        sessionActive = false,
        receiverPaused = false,
        packageName = "io.github.litecast",
    )

    @Test
    fun home() = shot("home") { HomeContent(readyHome, {}, {}, {}, {}, {}) }

    @Test
    fun homeNeedsPopUpPermission() = shot("home-popup") {
        HomeContent(readyHome.copy(canPopUp = false, sessionActive = true), {}, {}, {}, {}, {})
    }

    @Test
    fun settings() = shot("settings") {
        SettingsContent(SettingsUi(receiverOn = true, dlna = true, boot = true, canPopUp = false, pin = false, version = "0.1.0"),
            {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun videoOverlay() = shot("player-video") {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF34506B), Color(0xFF14202B), Color(0xFF3A2A1E))))) {
            VideoOverlay(
                title = "1818黄金眼 · 2026-10-24",
                source = "DLNA",
                info = VideoPlaybackInfo(positionMs = 754_000, durationMs = 2_712_000, playing = false),
                seekTarget = null,
                quality = "1080P",
            )
        }
    }

    @Test
    fun liveError() = shot("player-retry") {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
            ErrorCard("连接中断，5 秒后重试…")
        }
    }

    @Test
    fun slowStart() = shot("player-slow") {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
            ErrorCard("视频一直没加载出来。如果是油管这类境外网站的视频，需要电视本身能访问那个网站；也可以改用屏幕镜像。")
        }
    }

    @Test
    fun fatalError() = shot("player-error") {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
            ErrorCard("链接已失效或被拒绝，请在手机上重新投屏。", fatal = true)
        }
    }

    @Test
    fun mirroring() = shot("player-mirror") {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF141E30))))) {
            MirrorBadge()
            BackHint(visible = true)
        }
    }

    @Test
    fun music() = shot("player-music") {
        MusicContent(
            track = TrackInfo(title = "夏夜晚风", artist = "示例歌手", album = "示例专辑", coverArt = fakeCover()),
            position = 95_000, duration = 269_000, playing = true, backHint = false,
        )
    }

    @Test
    fun pin() = shot("player-pin") {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { PinView("4821") }
    }

    private fun fakeCover(): Bitmap {
        val bmp = Bitmap.createBitmap(260, 260, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 260f, 260f, 0xFFE8A87C.toInt(), 0xFF41B3A3.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, 260f, 260f, paint)
        return bmp
    }
}

/** Same screens in English, to catch longer strings overflowing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
class ScreenshotEnglishTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun optIn() = Assume.assumeTrue(System.getProperty("litecast.screenshots") == "true")

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { LiteCastTheme { content() } }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private val home = HomeUi(
        name = "Living Room TV · LiteCast",
        airplay = AirPlayService.ServerState.RUNNING,
        dlna = DlnaRenderer.Status.READY,
        dlnaEnabled = true,
        ip = "192.168.1.20",
        canPopUp = false,
        sessionActive = false,
        receiverPaused = false,
        packageName = "io.github.litecast",
    )

    @Test
    fun homeEnglish() = shot("en-home-popup") { HomeContent(home, {}, {}, {}, {}, {}) }

    @Test
    fun unreachableEnglish() = shot("en-player-unreachable") {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
            ErrorCard("The TV cannot reach the video's server. Sites like YouTube must be reachable from the TV itself; screen mirroring still works.", fatal = true)
        }
    }

    @Test
    fun videoEnglish() = shot("en-player-video") {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2733))) {
            VideoOverlay("Evening News", "AirPlay", VideoPlaybackInfo(playing = true, live = true, seekable = false), null, quality = "4K")
        }
    }
}
