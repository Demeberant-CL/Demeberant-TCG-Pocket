package com.example.ui

/** A small screen history, rooted at Home. Revisiting a screen removes navigation loops. */
object AppNavigation {
  fun open(history: List<Int>, destination: Int): List<Int> {
    require(destination in 0..5)
    val index = history.indexOf(destination)
    return if (index >= 0) history.take(index + 1) else history + destination
  }

  fun back(history: List<Int>): List<Int> = history.dropLast(1).ifEmpty { listOf(0) }
}
