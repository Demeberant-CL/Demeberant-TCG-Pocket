# Integración de Pokémon Zone en la app Android principal

Integración validada en el teléfono e incorporada en main. La rama integration/pokemon-zone/20261008 conserva la entrega original; las copias de prueba permanecen independientes.

Mantiene applicationId com.aistudio.tcgpocket2.kxmpzq, la firma persistente publicada, la base de datos Room versión 5 y las reglas originales de respaldo. El código de versión aumenta en CI. No desinstalar para actualizar: se conservan los datos privados existentes.

Entrada: Sincronizar colección. La primera conexión debe realizarse en el visor de la app principal, porque no puede compartir la sesión privada de la copia de prueba. Conectar cuenta, abrir View profile / Cards y sincronizar. Después basta el botón principal; Sync confirmado → cartas listas → recorrido incremental debajo de la pantalla de progreso → importación y verificación atómica. No se importan recorridos cancelados, interrumpidos, con timeout, vacíos o de otro perfil.

Resultado copiable y alternativa JSON: Colección → Ajustes → Pokémon Zone: resultado / importar JSON → Copiar resultado para el chat. El diagnóstico breve también se puede copiar en el visor. No se necesita exportar archivos para el flujo normal.

Cantidades incluidas se reemplazan, no se suman. Cartas ausentes, deseos, fechas de adquisición, mazos y ajustes permanecen. No se cambia el esquema ni se usa migración destructiva. Se mantiene la posibilidad de exportar/restaurar el respaldo completo existente de la app.

El recorrido conserva la misma WebView al rotar y usa el servicio foreground dataSync ya validado al minimizar/bloquear pantalla, mientras Android no termine el proceso. Cancelar conserva lo guardado. El guardado breve es una transacción; Cancelar queda deshabilitado durante ese paso.

La copia de prueba completó las pruebas reales del usuario: Sync y guardado de 1343 cartas distintas, 2440 copias, 24 sets; reimportación, persistencia, giro, uso de otra app y cancelación sin alterar la colección. Estos números no son un límite: cambian si cambia la cuenta del juego. El acceso con Google en navegador externo no comparte cookies con el visor; se conserva el acceso con contraseña y la vinculación Nintendo previamente probados. No se leen ni guardan contraseñas ni tokens.

Pruebas: 146 Android (incluye conservación de deseos/mazos/ausentes, cantidades, transacciones y rollback) y 19 de los selectores/Sync/recorrido. El APK se publica solo con pruebas, lint y firma verificados. Actualización y sincronización comprobadas en la app principal por el usuario: 1343 cartas distintas, 2440 copias y 24 sets, con deseos, mazos y ajustes conservados.
