package com.example

import com.example.ui.AppNavigation
import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
  @Test fun editorToAiReturnsToEditorThenHome() {
    val editor = AppNavigation.open(listOf(0), 2)
    val assistant = AppNavigation.open(editor, 3)
    assertEquals(editor, AppNavigation.back(assistant))
    assertEquals(listOf(0), AppNavigation.back(editor))
    assertEquals(listOf(0), AppNavigation.back(listOf(0)))
  }

  @Test fun revisitingTabsRemovesLoopsAndReselectionDoesNotAddHistory() {
    val history = listOf(0, 1, 2, 3, 4)
    assertEquals(history, AppNavigation.open(history, 4))
    assertEquals(listOf(0, 1, 2), AppNavigation.open(history, 2))
    assertEquals(listOf(0), AppNavigation.open(history, 0))
    assertTrue(runCatching { AppNavigation.open(history, 6) }.isFailure)
  }

  @Test fun metaReturnsToItsOriginAndAcceptedProposalReturnsToExistingEditor() {
    val tools = AppNavigation.open(listOf(0), 4)
    assertEquals(tools, AppNavigation.back(AppNavigation.open(tools, 5)))
    assertEquals(listOf(0), AppNavigation.back(AppNavigation.open(listOf(0), 5)))
    assertEquals(listOf(0, 2), AppNavigation.open(listOf(0, 2, 3), 2))
  }
}
