package com.lucasdss.ftpmusic.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADR-0143: Local mini bottom pad must flow into LazyColumn afterContentPadding
 * so scrolled-to-end content stays above the reveal remasure delta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class MiniPlayerBottomContentPaddingComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Local mini bottom pad reaches Lazy afterContentPadding`() {
        val miniPad = 64.dp
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            CompositionLocalProvider(LocalMiniPlayerBottomContentPadding provides miniPad) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pad_list"),
                        contentPadding = PaddingValues(
                            bottom = LocalMiniPlayerBottomContentPadding.current,
                        ),
                    ) {
                        items(8) { index ->
                            Text(
                                text = "Row $index",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .testTag("row_$index"),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val miniPadPx = with(composeRule.density) { miniPad.roundToPx() }
        assertEquals(miniPadPx, listState.layoutInfo.afterContentPadding)
        assertTrue(listState.layoutInfo.afterContentPadding > 0)
    }

    @Test
    fun `zero Local leaves Lazy afterContentPadding empty`() {
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            CompositionLocalProvider(LocalMiniPlayerBottomContentPadding provides 0.dp) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = LocalMiniPlayerBottomContentPadding.current,
                    ),
                ) {
                    items(4) { index ->
                        Text("Row $index", Modifier.height(40.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, listState.layoutInfo.afterContentPadding)
    }
}
