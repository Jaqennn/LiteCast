/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.tv

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.service.AirPlayService
import io.github.litecast.LiteCast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Preserve each rendered frame: an eventual false value can conceal the crashing frame. */
@RunWith(AndroidJUnit4::class)
class PlayerBindingTest {
    private val services = ServiceTestRule()
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(services).around(compose)
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private var savedBoot: Boolean? = null
    private var savedReceiver: Boolean? = null

    @Before fun isolateFromQueuedBootBroadcasts() {
        savedBoot = if (prefs.contains(Prefs.BOOT_AUTO_START.key)) prefs.getBoolean(Prefs.BOOT_AUTO_START.key, true) else null
        savedReceiver = if (prefs.contains(LiteCast.PREF_RECEIVER_ENABLED)) prefs.getBoolean(LiteCast.PREF_RECEIVER_ENABLED, true) else null
        prefs.edit().putBoolean(Prefs.BOOT_AUTO_START.key, false)
            .putBoolean(LiteCast.PREF_RECEIVER_ENABLED, false).commit()
    }

    @After fun restoreReceiverPreferences() {
        val edit = prefs.edit()
        savedBoot?.let { edit.putBoolean(Prefs.BOOT_AUTO_START.key, it) } ?: edit.remove(Prefs.BOOT_AUTO_START.key)
        savedReceiver?.let { edit.putBoolean(LiteCast.PREF_RECEIVER_ENABLED, it) } ?: edit.remove(LiteCast.PREF_RECEIVER_ENABLED)
        edit.commit()
    }

    @Test fun disconnectResetsTheFirstFrameAndIgnoresLateOldFlowValues() {
        val old = MutableStateFlow(41)
        val owner = mutableStateOf<StateFlow<Int>?>(old)
        val frames = mutableListOf<Pair<StateFlow<Int>?, Int>>()
        compose.setContent {
            val source = owner.value
            val value = source.collectOr(-1)
            SideEffect { frames.add(source to value) }
            Text(value.toString())
        }
        compose.onNodeWithText("41").assertExists()
        compose.runOnIdle { owner.value = null }
        compose.onNodeWithText("-1").assertExists()
        compose.runOnIdle { old.value = 99 }
        compose.waitForIdle()
        compose.runOnIdle {
            val detached = frames.filter { it.first == null }
            assertTrue(detached.isNotEmpty())
            assertTrue("No disconnected frame may retain an old service value", detached.all { it.second == -1 })
        }
    }

    @Test fun replacementFlowStartsWithItsOwnCurrentValue() {
        val old = MutableStateFlow(41)
        val replacement = MutableStateFlow(7)
        val owner = mutableStateOf<StateFlow<Int>?>(old)
        val frames = mutableListOf<Pair<StateFlow<Int>?, Int>>()
        compose.setContent {
            val source = owner.value
            val value = source.collectOr(-1)
            SideEffect { frames.add(source to value) }
            Text(value.toString())
        }
        compose.onNodeWithText("41").assertExists()
        compose.runOnIdle { owner.value = replacement }
        compose.onNodeWithText("7").assertExists()
        compose.runOnIdle { old.value = 99 }
        compose.waitForIdle()
        compose.runOnIdle {
            val changed = frames.filter { it.first === replacement }
            assertTrue(changed.isNotEmpty())
            assertTrue("The old value must not leak into a replacement receiver", changed.all { it.second == 7 })
        }
    }

    @Test fun activeVideoSurvivesRepeatedServiceUnbindAndRebind() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binder = services.bindService(Intent(context, AirPlayService::class.java)) as AirPlayService.LocalBinder
        val service = binder.service
        @Suppress("UNCHECKED_CAST")
        val playing = AirPlayService::class.java.getDeclaredField("_videoPlaybackActive").apply { isAccessible = true }
            .get(service) as MutableStateFlow<Boolean>
        val bound = mutableStateOf<AirPlayService?>(service)
        var finishes = 0
        playing.value = true
        compose.setContent { LiteCastTheme { PlayerScreen(bound.value, null) { finishes++ } } }
        compose.onNodeWithTag("player-VIDEO").assertExists()
        repeat(6) {
            compose.runOnIdle { bound.value = null }
            compose.onNodeWithTag("player-IDLE").assertExists()
            compose.runOnIdle { bound.value = service }
            compose.onNodeWithTag("player-VIDEO").assertExists()
        }
        compose.runOnIdle { bound.value = null }
        compose.onNodeWithTag("player-IDLE").assertExists()
        compose.runOnIdle { playing.value = false; assertEquals(0, finishes) }
    }
}
