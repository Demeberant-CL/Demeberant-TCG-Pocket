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
- Web: `npm test`, `npm run build` y prueba de navegador Chromium.
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
  Los detalles e imágenes dependen de TCGdex. Interfaz, imágenes y detalles en español fijo.
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
- Draft, giroscopio/shaders y winrates de torneos quedan fuera de esta ampliación. QR de mazos implementado posteriormente.

## Auditoría integral del PR #1

Cobertura automática: configuración y persistencia DataStore; filtros/cantidades/CSV;
respaldos actuales y heredados y restauración idempotente; edición/codec de mazos;
validación IA de IDs/cantidades/sustituciones, preparación de consulta y JSON externo;
QR binario contra dos fixtures conocidos, mapa completo, imagen PNG decodificada;
sandbox y canjes sin escrituras de colección; diagnóstico redactado, FileProvider y permisos.
GitHub Actions vuelve a ejecutar pruebas Android, Lint, compilación, pruebas web/servidor y Chromium.
Los resultados exactos del commit final están en la descripción del PR y Actions.
Las pruebas Android usan Robolectric/Room/DataStore: no equivalen a probar la interfaz en un teléfono.
Solo se emplean datos sintéticos de prueba; no hay llamadas pagadas de IA.

Probado en dispositivo por esta auditoría: ninguno. Entorno local desconectado, sin teléfono
ni sesión de Pokémon TCG Pocket disponibles. Pendientes: selector y persistencia en teléfono,
importar/exportar mediante el selector Android real, copiar/pegar consulta con una IA externa,
compartir PNG/TXT a otra app y escanear QR dentro del juego.
Chromium prueba realmente la web; bloquea servicios externos para no compartir datos.
Los logs de CI ahora detallan ID, mensaje y localización de cada hallazgo de Lint.
La web elimina idioma y Azul, conserva Automático en respaldos y sigue los cambios del sistema.
Chromium comprueba todos los filtros, persistencia tras recargar y cambios reales de prefers-color-scheme.
El portapapeles Android y las pantallas Compose requieren una prueba manual; no se confunden con las pruebas de dominio.

### Revisión de advertencias de Lint

Revisión del informe completo: 0 errores y 47 advertencias; no se han ocultado ni suprimido.

| Grupo | Cantidad | Evaluación |
| --- | ---: | --- |
| SDK objetivo (`OldTargetApi`) | 1 | Cambio de nivel objetivo pendiente de validación de comportamientos en dispositivo. |
| Versiones de Gradle/AGP, bibliotecas y herramientas | 21 | Avisos de versiones disponibles; no indican por sí mismos fallos funcionales. Actualización coordinada pendiente. |
| Recursos sin uso y directorio v26 redundante | 11 | Limpieza de recursos pendiente; sin modificación de datos, firma o identidad de paquete. |
| Iconos: forma, capa monocroma y ubicación | 8 | Presentación del lanzador; revisar en dispositivos con iconos adaptativos. |
| Estilo KTX y catálogo TOML | 6 | Recomendaciones de mantenimiento; no son errores de ejecución. |

Los mensajes y localizaciones exactos están en `lint-results-debug.xml` (artefacto
`android-reports`) y como `LINT_FINDING` en Actions.
Se corrigió el fallo reproducible de configuración web: retirada de Azul/idioma,
restauración del modo Sistema y seguimiento del sistema. La nueva prueba de Chromium espera
el evento asíncrono de cambio de apariencia, sin temporizadores fijos para asumir el resultado.
La prueba FileProvider limpia únicamente la caché estática del proveedor en Robolectric:
cada aplicación de prueba tiene un directorio distinto; no cambia la app ni archivos del usuario.

## Seguimiento de pruebas del usuario y simplificación

Reportado por el usuario en teléfono: instalación, temas, filtros, importación CSV,
guardar/restaurar respaldo, guardar/compartir PNG y compartir TXT correctos. Editor e IA
resultaron difíciles de usar; se simplifican con acciones, ayudas e imágenes. Se añade guardar TXT.
Estos resultados son reportados por el usuario, no pruebas físicas realizadas por Codex.

QR 29235: aceptado por el juego según el usuario. 29230, 29228 y 29227: rechazados,
con exactamente el mismo payload entre los tres. Decodificación local independiente zxing-cpp:
los cuatro PNG se leen como QR versión 9/H, máscara 1; ambos contenidos tienen 20 cartas,
una energía y 64 bytes sin sobrantes. El contenido aceptado tiene energía Rayo (4) y el
rechazado Psíquico (5), ambas admitidas por el formato documentado. Esto no demuestra
que la energía sea la causa. No se afirma haber corregido la aceptación del juego.
Pruebas nuevas comparan ambos payloads y decodifican sus nuevas imágenes principal/alternativa.
Se omite ECI para Base64 ASCII y se cambia la máscara en la alternativa, conservando bytes.

Pruebas nuevas de borrador local: cantidades actuales, límite por nombre incluidas variantes,
preevolución ausente, datos insuficientes, energías y ausencia de mutación de colección.
TXT guardado mediante ContentResolver: UTF-8, contenido esperado y ausencia de mensajes privados.
CI vuelve a ejecutar pruebas Android, Lint, compilaciones y Chromium; resultados finales en PR.

Pendientes de nueva prueba física: editor visual, Crear con mis cartas, sugerencias de energías,
flujo IA guiado/portapapeles, navegación a la IA, tutoriales, guardar TXT y ambos QR en el juego.
Datos desconocidos no se inventan. La sugerencia de energías usa el tipo, no costes de ataque
completos; Dragón/solo Incoloro/datos desconocidos necesitan revisión manual. La causa del
rechazo del QR y la calidad estratégica siguen pendientes.

La regresión encontró que una máscara alternativa válida confundía el detector de ZXing.
Reproducción local con ZXing 3.5.3: para el payload reportado como aceptado, máscaras 0 y 4
fallan en la detección normal aunque el símbolo es válido; las demás se decodifican. Para
el payload rechazado por el juego, las ocho se decodifican localmente. La exportación ahora
prueba máscaras y solo devuelve imágenes que decodifican exactamente el payload; la
alternativa selecciona la segunda máscara legible. Esta corrección no prueba aceptación en el juego.
