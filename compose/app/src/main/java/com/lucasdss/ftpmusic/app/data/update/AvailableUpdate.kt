package com.lucasdss.ftpmusic.app.data.update

/** Play-confirmed update ready to surface in the issue tray (ADR-0140). */
data class AvailableUpdate(val versionCode: Int, val flexibleAllowed: Boolean)
