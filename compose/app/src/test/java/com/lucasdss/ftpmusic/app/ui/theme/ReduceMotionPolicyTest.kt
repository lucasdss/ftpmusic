package com.lucasdss.ftpmusic.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReduceMotionPolicyTest {
    @Test
    fun `animator scale zero reduces`() {
        assertTrue(ReduceMotionPolicy.shouldReduce(animatorDurationScale = 0f))
    }

    @Test
    fun `transition scale zero reduces`() {
        assertTrue(
            ReduceMotionPolicy.shouldReduce(
                animatorDurationScale = 1f,
                transitionAnimationScale = 0f,
            ),
        )
    }

    @Test
    fun `appearance force reduces even when scales normal`() {
        assertTrue(
            ReduceMotionPolicy.shouldReduce(
                animatorDurationScale = 1f,
                transitionAnimationScale = 1f,
                appearanceForceReduce = true,
            ),
        )
    }

    @Test
    fun `normal scales do not reduce`() {
        assertFalse(ReduceMotionPolicy.shouldReduce(1f, 1f, false))
    }
}
