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
- No hay conexión con cuenta del juego, ejecución de canjes ni sincronización
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

## IA conectada (03-10-2026)

Android ofrece Gemini, OpenAI y APIs compatibles con Chat Completions desde el asistente, sin copiar consultas ni pegar JSON. Cada usuario configura su propia clave y modelo; Gemini es la selección inicial, sin garantía de cuota gratuita si habilita facturación. La conexión se cifra AES-GCM con Android Keystore en noBackupFilesDir y queda fuera de respaldos y diagnósticos. Cambiar proveedor vacía el campo de clave; Guardar reemplaza la conexión anterior.

Consulta solo tras pulsación y confirmación, sin reintentos ni cambio de proveedor. Contexto máximo 60 KB y respuesta 4096 tokens / 100 KB. Las propuestas se validan contra las cantidades actuales después de recibir la respuesta. Los errores de cuota, permiso, modelo y truncamiento no importan mazos. No se registran cuerpos, claves ni excepciones privadas de la conexión.

Pendiente: pruebas reales con clave del usuario, límites y disponibilidad de modelos de su cuenta, persistencia de Android Keystore en teléfono. APIs que no usan Gemini generateContent o Chat Completions requieren un adaptador adicional (Claude nativo no incluido). La web conserva sus funciones actuales. No se realizan consultas reales ni pagadas en CI.

## Diagnóstico breve y revisión (03-10-2026)

Resumen para portapapeles/compartir texto/guardar TXT breve, máximo 6000 caracteres. Se analiza el registro por líneas en IO, con grupos acotados y últimos 12 tipos de fallo; no se copia mensaje, URL ni cuerpo. Room conserva sus errores pero deja de registrar cada consulta interna. El TXT completo y los archivos anteriores se conservan. Cola limitada con contador de omisiones y barrera de exportación no descartable; redacción precompilada y ampliada a Gemini/JSON.

Regresiones: inundación de 10000 consultas SQL seguida de QR/HTTP 429, agrupación y tamaño máximo, ausencia de mensajes/secretos, informe vacío con límites explícitos, redacción Gemini/JSON, guardado UTF-8 y colección modificada después de una propuesta IA. La apertura del borrador lee la colección actual y se bloquea si faltan copias, sin cambiar cantidades.

Pendiente en teléfono: copiar y pegar el resumen aquí, compartir como texto y guardar el TXT breve. No se afirma solucionar el cargador de adjuntos de ChatGPT. Los resultados completos y el APK de este commit se registran en Actions y la descripción actual del PR.

## Perfiles IA, navegación y torneos
- La conexión cifrada anterior se lee como perfil legado; guardar o seleccionar conserva los demás perfiles. Se conserva el alias Keystore y el archivo cifrado, sin cambiar firma/applicationId.
- Descubrimiento de modelos mediante GET autenticado, sin generación pagada; selección manual disponible si el proveedor no expone listado. El listado no garantiza acceso de generación ni precio.
- Limitless: petición real a /games, /tournaments y standings comprobada el 03-10-2026. POCKET, cartas set/number y energías reconocidos. Muestra hasta 12 torneos/30 días, caché atómica y adaptación solo con IDs conocidos. Estadísticas de registros de participantes, excluyen empates del porcentaje; no equivalen a ranked global. Formato null se admite si no declara reglas especiales; no garantiza ausencia de reglas no declaradas.
- Ayuda separada en Más, navegación principal Colección/Mazos/Meta/IA/Más e icono vectorial propio.
- Pendiente en teléfono: migración real del perfil existente, cambio de proveedor/modelo, reinicio, pantalla Meta y nuevos PNG en el juego. No se realizaron consultas pagadas a proveedores IA.


Modelos y guías (2026-10-03): el listado aplica un filtro conservador de modelos de texto: Gemini requiere generateContent exacto y familia estándar con JSON; OpenAI excluye modelos especializados/solo Responses. Compatible requiere metadatos explícitos de endpoint y salida texto; cuando faltan, el usuario puede introducir el ID manualmente. No se afirma cuota, precio ni éxito de generación por listar modelos; no se hacen probes pagados. Los perfiles existentes no se cambian. Guía usa libro y texto en lugar de ?; Más muestra tarjetas en dos columnas inspiradas en las capturas del juego, y los iconos de Mazos/Más representan su función. Prueba adicional del filtro; revisión real de UI en teléfono pendiente.


Avatares de perfil (2026-10-03): seis personajes originales de estilo entrenador, recurso WebP incluido en el APK (sin descargas). Toca el avatar en Colección, elige y confirma Usar avatar; Cancelar conserva la elección previa. DataStore guarda un ID estable, cambiar tema no lo altera. Respaldo JSON incluye avatar; restaurar un respaldo antiguo sin ese campo conserva el avatar actual. IDs desconocidos vuelven al avatar predeterminado. Dos pruebas cubren persistencia/independencia del tema, restauración antigua/nueva y contenido del respaldo. La propuesta de rediseño completa es una referencia visual; este lote implementa el selector de avatares, no todo el rediseño. Verificación visual en teléfono pendiente. Atlas creado mediante generación de imagen con seis retratos originales en cuadrícula 3x2, convertido a WebP para reducir el APK.

## Colección compacta e imágenes (2026-10-03)
- Encabezado compacto con detalles de perfil desplegables, filtros en buscador, repetidas en la fila de estados y contador de resultados filtrados.
- Cartas faltantes a color, sin bloqueo central; cantidades y deseos intactos.
- Una alternativa inglesa únicamente ante HTTP 404 del recurso traducido. Si falla, etiqueta Imagen no disponible; no se inventan rutas ni se modifican IDs guardados.
- Pendiente comprobación visual en teléfono y compilación CI.

## Mazos y editor visual (2026-10-03)
- Mis mazos usa una lista virtualizada con portada, energías guardadas y disponibilidad calculada por ID/cantidad. Eliminar requiere confirmación. Cartas desconocidas no se borran.
- Editor con Cartas/Energías/Notas, controles +/− accesibles y Guardar fijo abajo. Energías personalizadas, guardado de borrador, avisos y exportación se conservan.
- Cambiar cantidad mantiene el orden visual de las cartas; prueba cubre aumento, rechazo de cantidad >2 y retirada.
- La aceptación de una propuesta IA abre el editor directamente. No se realizan consultas IA por navegar o pulsar controles locales.
- Pendiente comprobación visual en teléfono y compilación CI.

## Acciones IA y pantalla simplificada (2026-10-03)
- Cabecera fija con perfil/proveedor/modelo activos; configuración en diálogo separado y filtro de cartas desplegable.
- Crear desde colección; Completar faltantes conserva copias disponibles del objetivo de 20; Mejorar usa la baraja de 1–20 como referencia libre y puede cambiar cartas.
- Mejora envía reference separado de target; no relaja validación de IDs/cantidades/básicos/evoluciones/energías/20 cartas. Replacements vacío en creación/mejora.
- Confirmación previa al envío y a reemplazar el borrador; sin consulta automática ni cambio de proveedor por fallo.
- Prueba nueva de elegibilidad y separación referencia/objetivo. Pendiente CI y teléfono/API real.

## Inicio y navegación (2026-10-03)
- Inicio muestra colección/copias/mazos desde los flujos locales, avatar guardado, borrador abierto y conexión IA activa. Accesos a colección, biblioteca, editor, IA, meta y guía.
- Barra de cinco destinos: Inicio/Colección/Mazos/IA/Más. Meta accesible desde Inicio y Más con vuelta al origen y botón Atrás. Tutoriales siguen aparte.
- Entrar en Inicio no consulta IA ni actualiza torneos; solo lee caché local de meta. Navegar al editor conserva el borrador. Biblioteca seleccionada por acceso directo Mis mazos.
- Pendiente compilación CI y revisión visual en teléfono.
