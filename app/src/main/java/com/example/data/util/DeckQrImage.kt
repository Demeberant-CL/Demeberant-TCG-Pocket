package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import com.example.data.repository.GeneratedDeck
import com.example.domain.PocketDeckQr
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

data class DeckQrImage(val payload: String, val bitmap: Bitmap, val png: ByteArray)

object DeckQrImages {
  fun create(context: Context, deck: GeneratedDeck, alternate: Boolean = false): DeckQrImage {
    val identities = context.assets.open("pocket-qr-entities.json").bufferedReader().use { PocketDeckQr.readIdentities(it.readText()) }
    val payload = PocketDeckQr.encode(PocketDeckQr.resolve(deck, identities))
    // Base64 is ASCII: omit the ECI charset segment, matching the reference byte format.
    val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
      EncodeHintType.QR_VERSION to 9, EncodeHintType.MARGIN to 4)
    if (alternate) {
      val primaryMask = com.google.zxing.qrcode.encoder.Encoder.encode(payload, ErrorCorrectionLevel.H, hints).maskPattern
      hints[EncodeHintType.QR_MASK_PATTERN] = (primaryMask + 1) % 8
    }
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 976, 976, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
      if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    return DeckQrImage(payload, bitmap, output.toByteArray())
  }
}
