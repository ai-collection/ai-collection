package com.vault.calc.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapUtils {

    /** Decodes [bytes] into a bitmap no larger than [reqW] x [reqH] (approximately). */
    fun decodeSampled(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calculateInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        if (w <= 0 || h <= 0) return 1
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while (halfH / sample >= reqH && halfW / sample >= reqW) {
                sample *= 2
            }
        }
        return sample.coerceAtLeast(1)
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
    }
}
