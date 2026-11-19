/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.tv

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import io.github.jqssun.airplay.Prefs
import io.github.litecast.LiteCast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "zh-rCN-w960dp-h540dp-land-television-xhdpi")
class RenameDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardDonePersistsTrimmedNameOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        var saves = 0
        compose.setContent {
            LiteCastTheme {
                RenameDialog("轻投", {}, { value ->
                    saves++
                    Prefs.SERVER_NAME.write(prefs, value)
                })
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("  书房  ")
        field.performImeAction()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals("书房", Prefs.SERVER_NAME.read(prefs))
            LiteCast.applyDefaults(context)
            assertEquals("书房", Prefs.SERVER_NAME.read(prefs))
        }
    }

    @Test fun remoteCanLeaveInputAndActivateSave() {
        var saved = ""
        compose.setContent { LiteCastTheme { RenameDialog("轻投", {}, { saved = it }) } }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("客厅")
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("保存").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("客厅", saved) }
    }

    @Test fun emptyNameCannotSaveButRestoreDefaultWorks() {
        var saved = ""
        compose.setContent { LiteCastTheme { RenameDialog("轻投", {}, { saved = it }) } }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("   ")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("取消").assertIsFocused()
        compose.onNodeWithText("取消").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("恢复为轻投").assertIsEnabled().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("轻投", saved) }
    }
}
