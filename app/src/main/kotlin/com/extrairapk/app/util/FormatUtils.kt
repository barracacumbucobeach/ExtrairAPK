package com.extrairapk.app.util

import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(exponent.toDouble())
        return String.format("%.1f %s", value, units[exponent - 1])
    }

    fun formatDate(timestampMillis: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampMillis))
    }
}
