package com.example.zoneimport

import android.content.Context
import android.os.Looper
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
          ZoneCollectionImport.apply(AppDatabase.getDatabase(app), plan)
          val result = "SINCRONIZACIÓN E IMPORTACIÓN OK\n${plan.uniqueCards} cartas únicas · ${plan.totalCopies} copias · ${plan.sets} sets\n" +
            "Cantidades verificadas en ambas tablas.\nDuplicados omitidos: ${plan.duplicates}.\n" +
            "Reimportar reemplaza cantidades; no suma. Cartas ausentes, deseos y mazos se conservan.\n" +
            "Guardado: ${android.os.SystemClock.elapsedRealtime() - start} ms.\nApp: com.aistudio.tcgpocket2.kxmpzq"
          app.getSharedPreferences("zone-import-test", Context.MODE_PRIVATE).edit()
            .putString("result", result).putString("player", plan.playerId).apply()
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
