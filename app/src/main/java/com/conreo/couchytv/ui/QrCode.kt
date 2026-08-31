package com.conreo.couchytv.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** High-contrast QR for scanning from a phone aimed at the TV. */
fun encodeQrBitmap(text: String, px: Int = 512): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, hints)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565)
    val black = android.graphics.Color.BLACK
    val white = android.graphics.Color.WHITE
    for (x in 0 until px) {
        for (y in 0 until px) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) black else white)
        }
    }
    return bmp.asImageBitmap()
}
