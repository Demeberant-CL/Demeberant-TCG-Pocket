package com.example.data.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readBytesBounded(limit: Int): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  while (true) {
    val count = read(buffer)
    if (count < 0) break
    require(output.size() + count <= limit) { "Archivo demasiado grande." }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}
