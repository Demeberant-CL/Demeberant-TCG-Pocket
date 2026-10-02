# Validación y límites de la aplicación

Rama: `fix/collection-decks-validation-20261001`.
[Pull request #1](https://github.com/Demeberant-CL/Demeberant-TCG-Pocket/pull/1).
[Resultados de Actions](https://github.com/Demeberant-CL/Demeberant-TCG-Pocket/actions).
El resultado de la última revisión se registra en los trabajos y en la descripción del PR.

## Comprobaciones automáticas

- Android: `testDebugUnitTest lintDebug assembleDebug`. Actions resume el número de
  pruebas, errores, omisiones, errores de Lint y versión del APK en los logs.
- Pruebas de conservación de datos: migraciones 3→4 y 4→5, IDs canónicos, CSV con comillas,
  cantidades y Deseos, restauración sin borrar cartas ausentes, mazos duplicados y legado.
- Catálogo: 4317 IDs únicos en 24 colecciones (incluidas promociones), fuente comunitaria
  identificada, sin importar PS constantes ni daños sin verificar.
- Mazos: cantidades válidas, formatos heredado y JSON, energías y borradores.
- Canjes: reserva de copias, propuesta recíproca por rareza y ausencia explícita en CSV.
- Probabilidades: casos exactos de aperturas independientes y robo sin reemplazo.
- Web: `npm test` (10 pruebas), `npm run build` y prueba de navegador Chromium.
- Navegador: editar cantidad, Deseos, exportar CSV, guardar y reabrir mazos tras recargar,
  energías, respaldo completo, restauración sin duplicar, temas, canjes y calculadora.
  Los servicios externos se bloquean durante esta prueba para comprobar el funcionamiento
  local; esto no valida la disponibilidad real de las imágenes ni de la API.
- Firma: certificado del APK comparado con la clave restaurada y con el certificado
  de la versión estable instalada:
  `D3AD80D2FAB19C1E2F29BD3B78C92B57CCDA271374194B57FE019D63C737106A`.
  El APK solo se sube si las comprobaciones y la firma son correctas.

## Artefactos

`android-apk`: APK y certificado; `android-reports`: pruebas y Lint.
`web-app`: web compilada; `web-reports`: capturas y resultado de Chromium.
No se fusiona en main ni se despliega la web automáticamente.

## Límites pendientes

- La instalación y los flujos nuevos de Android requieren validación en un teléfono físico.
- Catálogo comunitario: metadatos de tipo/evolución incompletos en expansiones recientes.
  Los detalles e imágenes dependen de TCGdex. Interfaz en español; cambia el idioma de imágenes.
- Las plantillas A1 son históricas, sin afirmar que representan el meta actual.
- No hay integración real con Gemini, cuenta del juego, ejecución de canjes ni sincronización
  automática entre Android y web.
- No hay tasas oficiales de apertura importadas. La calculadora requiere una tasa total
  por sobre introducida por el usuario y no simula la garantía de básico ni efectos.
- Migraciones desde versiones Room 1/2: se necesitan sus esquemas originales. Se conserva
  el bloqueo en lugar de borrar datos automáticamente. Las migraciones 3→4 y 4→5 se prueban.
- APK debug con firma de pruebas persistente; no es una distribución de Google Play.
- El entorno local dejó de responder durante esta ampliación; las comprobaciones finales
  se ejecutan realmente en GitHub Actions.

## Validación histórica del parche original

El parche sobre `e2b77e420d120cf6f5fcf57fe4975f65846cb5a0` pasó 15 pruebas,
Lint con 0 errores y 26 advertencias, compilación Android y compilación web.
Ese resultado corresponde al parche original; la ampliación actual tiene nuevas pruebas.

## Módulo avanzado: diagnóstico, IA y práctica

Validación completa del commit 190abe116414dc3f731e54ca76b0d6e13e05e722:
[Actions #31](https://github.com/Demeberant-CL/Demeberant-TCG-Pocket/actions/runs/36948010001).
41 pruebas Android, 0 fallos/errores/omisiones; Lint 0 errores y 44 advertencias;
APK 1.0.1031 compilado y certificado persistente verificado.
10 pruebas web, 7 pruebas del servidor IA, compilación web y regresión Chromium correctas.
Las comprobaciones del commit final se consultan en Actions y el PR.

- Room 4→5 conserva inventario, Deseos y mazos; añade exclusivamente caché de reglas.
  Esquema 5 generado por Room y conservado en el repositorio.
- Pruebas nuevas: filtros SQL, heurísticas de roles, migración real desde esquema 4,
  tablero de 20 cartas y conservación de zonas, JSON IA, cantidades disponibles,
  sustituciones exactas, HTTP, cancelación y ocultación de credenciales en diagnóstico.
- Logs diarios internos con rotación y cola limitada: HTTP solo metadatos (también imágenes),
  fallos Room y excepciones fatales. Exportación voluntaria TXT mediante FileProvider.
  No registra cuerpos de peticiones/respuestas, tokens ni colección. La cola puede descartar
  eventos antiguos durante ráfagas; un crash se registra pero no recupera la interfaz.
- IA: servidor Node con OpenAI Responses y Structured Outputs; clave en servidor.
  Android valida IDs, 20 cartas, copias, evoluciones y sustituciones antes de abrir un borrador.
  Las pruebas usan un proveedor simulado: no validan llamadas reales ni calidad estratégica.
  Servidor no desplegado: requiere HTTPS, clave OpenAI y token de acceso. No se realizaron
  solicitudes con coste. Uso personal; faltan autenticación y cuotas para servicio multiusuario.
  El meta lo aporta el usuario; no hay scraping silencioso ni estadísticas actuales verificadas.
- Efectos: etiquetas estimadas a partir de textos; depende de detalles TCGdex almacenados.
  Indexación manual de hasta 25 cartas nuevas por lote. No se considera catálogo completo
  ni taxonomía oficial; metadatos incompletos pueden limitar propuestas.
- Sandbox manual de un jugador: mano, activo, banca, descartes, energía, daño y deshacer.
  Mano inicial con básico mediante aproximación explícita. No implementa ataques ni todas las reglas.
- Estas pantallas avanzadas están en Android, menú Análisis. La web conserva sus funciones
  anteriores; no incluye aún IA, sandbox o filtros por efectos.
- Draft, giroscopio/shaders, QR y winrates de torneos quedan fuera de esta ampliación.
