package com.lucasdss.ftpmusic.app.playback

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** ADR-0146: packaged Auto media declaration must ship with the APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomotiveAppDescRobolectricTest {

    @Test
    fun `automotive_app_desc declares media use`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resId = context.resources.getIdentifier(
            "automotive_app_desc",
            "xml",
            context.packageName,
        )
        assertTrue("automotive_app_desc.xml missing", resId != 0)
        val parser = context.resources.getXml(resId)
        var foundMedia = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "uses") {
                if (parser.getAttributeValue(null, "name") == "media") {
                    foundMedia = true
                    break
                }
            }
            event = parser.next()
        }
        assertTrue("uses name=media missing", foundMedia)
    }

    @Test
    fun `manifest meta-data points at automotive_app_desc`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ai = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val meta = ai.metaData
        assertNotNull(meta)
        val resId = meta!!.getInt("com.google.android.gms.car.application")
        assertTrue("car.application meta-data missing", resId != 0)
        assertEquals(
            context.resources.getIdentifier("automotive_app_desc", "xml", context.packageName),
            resId,
        )
    }
}
