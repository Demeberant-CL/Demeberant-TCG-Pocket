package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import com.example.data.repository.GeneratedDeck
import com.example.domain.PocketDeckQr
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

data class DeckQrImage(val payload: String, val bitmap: Bitmap, val png: ByteArray)

object DeckQrImages {
  fun create(context: Context, deck: GeneratedDeck, alternate: Boolean = false): DeckQrImage {
    val identities = context.assets.open("pocket-qr-entities.json").bufferedReader().use { PocketDeckQr.readIdentities(it.readText()) }
    val payload = PocketDeckQr.encode(PocketDeckQr.resolve(deck, identities))
    // Base64 is ASCII: omit ECI, matching the reference byte format. Keep v9/H and a quiet zone.
    val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
      EncodeHintType.QR_VERSION to 9, EncodeHintType.MARGIN to 4)
    val preferred = com.google.zxing.qrcode.encoder.Encoder.encode(payload, ErrorCorrectionLevel.H, hints).maskPattern
    // Some valid masks confuse finder detection. Export only images our normal reader can read.
    var readable = 0
    for (mask in (listOf(preferred) + (0..7).toList()).distinct()) {
      hints[EncodeHintType.QR_MASK_PATTERN] = mask
      val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 976, 976, hints)
      val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) {
          // Dark cyan to blue on white: same modules, payload and quiet zone.
          val fraction = (index / matrix.width).toFloat() / (matrix.height - 1)
          android.graphics.Color.rgb((0 + 22 * fraction).toInt(), (123 - 48 * fraction).toInt(), (150 + 7 * fraction).toInt())
        } else android.graphics.Color.WHITE
      }
      val decoded = try {
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))).text
      } catch (_: ReaderException) { null }
      if (decoded != payload) continue
      readable++
      if (alternate && readable < 2) continue
      val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
      bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
      val output = ByteArrayOutputStream()
      check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
      return DeckQrImage(payload, bitmap, output.toByteArray())
    }
    error("No se encontró una imagen QR legible. No se ha exportado ninguna imagen.")
  }
}
