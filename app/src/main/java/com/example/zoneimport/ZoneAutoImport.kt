package com.example.zoneimport

import android.content.Context
import android.os.Looper
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.repository.CardCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The native scanner calls this only for a current, naturally finished, confirmed Sync. */
object ZoneAutoImport {
  fun interface Callback { fun onResult(ok: Boolean, message: String) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var active = false

  fun prepare(payload: String, expectedPlayer: String): ZoneImportPlan {
    require(payload.length <= ZoneCollectionImport.MAX_BYTES) { "Resultado demasiado grande." }
    val root = JSONObject(payload)
    require(root.opt("syncConfirmed") == true && root.opt("scanCompleted") == true) {
      "Recorrido incompleto o sincronización sin confirmar. No se importó nada."
    }
    val plan = ZoneCollectionImport.parse(payload)
    require(plan.playerId == expectedPlayer) { "La cuenta cambió durante la operación. No se importó nada." }
    return plan
  }

  internal fun additions(plan: ZoneImportPlan, before: Map<String, Int>): Pair<Int, Long> {
    var distinct = 0
    var copies = 0L
    for (row in plan.cards) {
      val id = com.example.data.util.CardId.normalize("${row.setCode}-${row.cardNumber}")
      val previous = before[id] ?: 0
      if (previous == 0 && row.quantity > 0) distinct++
      copies += maxOf(0L, row.quantity.toLong() - previous.toLong())
    }
    return distinct to copies
  }

  @JvmStatic fun start(context: Context, payload: String, expectedPlayer: String, callback: Callback): Boolean {
    check(Looper.myLooper() == Looper.getMainLooper())
    if (active) return false
    active = true
    val app = context.applicationContext
    scope.launch {
      try {
        val message = withContext(Dispatchers.IO) {
          val start = android.os.SystemClock.elapsedRealtime()
          CardCatalog.loadBundled(app)
          val plan = prepare(payload, expectedPlayer)
          val db = AppDatabase.getDatabase(app)
          var newCards = 0
          var addedCopies = 0L
          val stored = db.withTransaction {
            val before = db.inventoryDao().getAllCards().associate { it.cardId to it.quantity }
            val changes = additions(plan, before)
            newCards = changes.first; addedCopies = changes.second
            ZoneCollectionImport.apply(db, plan)
            db.inventoryDao().getAllCards().filter { it.quantity > 0 }
          }
          val importedIds = plan.cards.map { com.example.data.util.CardId.normalize("${it.setCode}-${it.cardNumber}") }.toSet()
          val retained = stored.count { it.cardId !in importedIds }
          val appSummary = if (retained > 0)
            "Total guardado en la app: ${stored.size} cartas distintas · ${stored.sumOf { it.quantity.toLong() }} copias.\n" +
              "$retained cartas previas fuera del recorrido se conservaron.\n"
            else ""
          val result = "SINCRONIZACIÓN E IMPORTACIÓN OK\n${plan.uniqueCards} cartas únicas · ${plan.totalCopies} copias · ${plan.sets} sets\n" +
            "Cantidades verificadas en ambas tablas.\nDuplicados omitidos: ${plan.duplicates}.\n" + appSummary +
            "Reimportar reemplaza cantidades; no suma. Cartas ausentes, deseos y mazos se conservan.\n" +
            "Guardado: ${android.os.SystemClock.elapsedRealtime() - start} ms.\nApp: com.aistudio.tcgpocket2.kxmpzq"
          app.getSharedPreferences("zone-import-test", Context.MODE_PRIVATE).edit()
            .putString("result", result).putString("player", plan.playerId)
            .putLong("synced_at", System.currentTimeMillis()).putInt("new_cards", newCards)
            .putLong("added_copies", addedCopies).apply()
          result
        }
        callback.onResult(true, message)
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        callback.onResult(false, "No se importó la colección: ${e.message?.take(200) ?: "error de validación o guardado"}")
      } finally { active = false }
    }
    return true
  }
}
