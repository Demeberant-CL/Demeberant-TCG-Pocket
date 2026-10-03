# Demeberant TCG Pocket

Aplicación Android y web para gestionar una colección de Pokémon TCG Pocket.

## Funciones

- Catálogo comunitario local de 4317 cartas y 24 colecciones (incluidas promociones), revisión del 01-10-2026.
- Colección: búsqueda, expansión y rareza; Todas, Tengo, Faltan, Deseos y repetidas.
- Edición de cantidades al tocar una carta, deseos, importación y exportación de CSV.
- Detalles de TCGdex con caché Android; se muestran solo datos recibidos, sin PS o daño inventados.
- Editor manual de mazos: hasta 20 cartas, dos copias por nombre, hasta tres energías, notas,
  borradores, mazos guardados y lista para compartir. Los mazos antiguos siguen siendo legibles.
- Android conserva plantillas históricas A1, identificadas como históricas.
- Canjes: reserva de una o dos copias y listas para ofrecer/buscar. Android y web comparan otro CSV
  y muestran propuestas recíprocas por rareza sin modificar ninguna colección.
- Análisis de sobres por faltantes, deseos y objetivos (Android), sin tasas no verificadas.
- Calculadora con tasa total por sobre introducida por el usuario y robo sin reemplazo.
- Respaldo JSON compatible entre Android y web: colección, deseos, mazos y preferencias.

## Datos y respaldo

Una instalación nueva empieza sin cartas poseídas. Importa tu CSV de Pokémon Zone desde Ajustes.
Se aceptan columnas Set, ID, Nombre, Rareza y Cantidad; Registrada es opcional y se deriva
de la cantidad. Deseos es opcional; si falta, se conserva el deseo anterior.
Las cartas ausentes de un CSV no se borran.

Guarda un respaldo JSON antes de trasladar datos. La restauración muestra una confirmación:
reemplaza cantidades y deseos de las cartas incluidas, conserva las cartas ausentes y los
mazos existentes, evita duplicar mazos idénticos y restaura preferencias.
No contiene contraseñas ni claves de firma.

Android usa Room y DataStore. La web usa el almacenamiento del navegador y no sincroniza
automáticamente con Android. Borrar los datos del navegador puede eliminar la colección local.
La versión web se inicia con `npm ci && npm run dev`; `npm run build` produce `dist/`.

## Validación y APK

`./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon`
`npm ci && npm test && npm run build`

Actions ejecuta esas comprobaciones y una prueba Chromium de los flujos web.
Los artefactos incluyen android-reports, android-apk, web-reports y web-app.
El APK mantiene el identificador de aplicación y se firma con el secreto
ANDROID_DEBUG_KEYSTORE_BASE64. No se publica APK si falta ese secreto.
Actions compara el certificado del APK con la clave restaurada antes de publicar.
El versionCode aumenta con el número de ejecución para admitir actualizaciones.

## Límites reales

- Es una herramienta de organización, no se conecta a tu cuenta del juego ni ejecuta canjes.
- Catálogo comunitario: puede contener errores. Los datos de tipo y evolución están incompletos
  en expansiones recientes. Las reglas especiales deben revisarse en el juego.
- Imágenes y detalles requieren conexión y disponibilidad de TCGdex. El resto de Android
  trabaja con datos locales; la web necesita cargar sus archivos al abrirse.
- Las plantillas A1 y sus etiquetas históricas no son el meta actual. Hay un enlace a torneos.
- Las conexiones IA requieren una clave propia; las pruebas de CI usan respuestas simuladas, sin consultas pagadas.
- No se incorporan tasas de apertura no verificadas. La calculadora no modela la garantía
  de Pokémon básico en la mano inicial, habilidades ni efectos.
- La web compilada se entrega como artefacto; no se despliega ni se fusiona en main automáticamente.

Fuentes y licencias: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Módulo avanzado: IA, sandbox, efectos y diagnóstico

En Análisis hay tres nuevas secciones: IA, Sandbox y Efectos.
IA conecta directamente a Gemini, OpenAI o una API compatible con Chat Completions.
Configura tu propia clave y modelo, indica tu objetivo y pulsa Crear con mi IA.
Para sustituir faltantes, abre un mazo de 20 cartas y usa Completar mazo.
La respuesta se valida contra tu colección y solo se abre como borrador.
El código del servidor opcional de `backend/` se conserva como herramienta independiente;
no se ha desplegado y ya no es necesario para la pantalla IA.

Sandbox practica con el mazo abierto de 20 cartas: mano, activo, banca de tres, descartes,
robo, turnos, marcadores de daño/energía y deshacer. Es un tablero manual de un jugador,
sin ejecución automática de ataques ni reglas. La garantía inicial de básico usa un
intercambio de carta como aproximación explícita. No modifica cantidades ni mazos guardados.

Efectos indexa hasta 25 cartas nuevas por solicitud y consulta Room por PS, tipo,
texto de ataques/habilidades y roles estimados (robo, curación, energía, milling, movilidad).
Una carta se indexa también al abrir sus detalles. Las etiquetas son heurísticas y
los resultados solo incluyen cartas con reglas disponibles en español.
La migración Room 4→5 crea la caché de reglas conservando colección y mazos.

Diagnóstico: logs diarios con escritura asíncrona, cola acotada, rotación y máximo de seis
archivos de aproximadamente 1 MB. Registra red (operación/HTTP/tiempo/tamaño), errores de Room y crashes no capturados antes de delegar al manejador Android.
No conserva cuerpos privados ni credenciales. Exportación TXT por FileProvider desde Ajustes;
el selector de compartir lo abre el usuario, sin enviar automáticamente.

Capas: `data/` (Room, HTTP y repositorios), `domain/` (validadores, roles y sandbox),
`ui/` (Presentation: ViewModels y Compose). ZXing se utiliza para la exportación QR de mazos descrita más abajo.
Draft, giroscopio y estadísticas de torneos no están incluidos.

## Configuración simplificada
Android y web utilizan español fijo para interfaz, imágenes y detalles. Configuración no guarda idioma.
El tema admite Claro, Oscuro y Automático (Sistema), con dos paletas Light/Dark.
Los ajustes antiguos Azul pasan a Claro; la migración elimina idioma y booleano redundante.
Los respaldos anteriores se aceptan sin recuperar el idioma retirado. Nuevos respaldos guardan solo el modo de tema.

## Exportar QR de mazo al juego

En Android: Mazos → Editor → Exportar QR para el juego. Abre un mazo guardado o creado,
completa 20 cartas y selecciona de una a tres energías. El diálogo muestra el QR y permite
Guardar PNG o Compartir PNG. No cambia la colección, no abre el juego y no garantiza que
la cuenta receptora tenga las cartas. El código usa entidades semánticas, por lo que el juego
puede elegir otra impresión del mismo Pokémon/Entrenador.

Formato binario comunitario compatible con los códigos de mazo de Pocket: Base64,
secciones Entrenadores/Pokémon/Energías, IDs u24 big-endian y offset de Entrenador.
Mapeo local de las 4317 impresiones del catálogo: sin solicitudes de red. Se rechazan cartas
sin mapeo, mazos incompletos, más de dos copias por nombre y energías no seleccionables.
El juego valida básicos, evoluciones y reglas especiales; la exportación no afirma legalidad completa.
ZXing genera QR convencional versión 9/H con margen blanco, sin adornos sobre el código.
La importación real en el juego sigue pendiente de una prueba en teléfono; las pruebas automáticas
comparan dos fixtures comunitarios capturados del juego y decodifican la imagen producida.

## Uso guiado y automatización

Ayuda y tutoriales está disponible en toda la app, y cada pantalla principal tiene ayuda contextual.
Mazos separa Mi mazo y Añadir cartas, muestra imágenes y ofrece Crear con mis cartas por tipo.
Es un borrador local con cantidades actuales, dos copias por nombre y preevoluciones conocidas;
no usa una IA ni promete una estrategia óptima. Las energías se sugieren por tipo conocido,
con opción de personalizarlas. Los costes de ataques y los Pokémon Dragón deben revisarse.
Plantillas A1 incluyen ahora su energía. Un mazo guardado conserva sus energías personalizadas;
los antiguos sin selección reciben una sugerencia al abrirse, sin modificar el original hasta guardar.

IA conectada evita copiar consultas o pegar JSON. Ajustes permite Guardar diagnóstico TXT
mediante el selector de archivos Android además de Compartir.

QR: el usuario informó que 29235 se importó y que 29230/29228/29227 fueron rechazados;
estos tres contienen exactamente el mismo payload. Todos se decodifican localmente, con 20
cartas y una energía válida. La causa del rechazo del juego no está demostrada. Se omite el
segmento ECI innecesario para ASCII para aproximarse al generador de referencia y se ofrece
Probar QR alternativo (otra máscara, mismo contenido). Ambas imágenes se decodifican en pruebas;
su aceptación real requiere una nueva prueba en el juego.

## IA conectada (03-10-2026)

Android ofrece Gemini, OpenAI y APIs compatibles con Chat Completions desde el asistente, sin copiar consultas ni pegar JSON. Cada usuario configura su propia clave y modelo; Gemini es la selección inicial, sin garantía de cuota gratuita si habilita facturación. La conexión se cifra AES-GCM con Android Keystore en noBackupFilesDir y queda fuera de respaldos y diagnósticos. Cada perfil conserva su clave y modelo. Cambiar proveedor prepara otra conexión; guardar un perfil existente lo actualiza sin borrar los demás.

Consulta solo tras pulsación y confirmación, sin reintentos ni cambio de proveedor. Contexto máximo 60 KB y respuesta 4096 tokens / 100 KB. Las propuestas se validan contra las cantidades actuales después de recibir la respuesta. Los errores de cuota, permiso, modelo y truncamiento no importan mazos. No se registran cuerpos, claves ni excepciones privadas de la conexión.

Pendiente: pruebas reales con clave del usuario, límites y disponibilidad de modelos de su cuenta, persistencia de Android Keystore en teléfono. APIs que no usan Gemini generateContent o Chat Completions requieren un adaptador adicional (Claude nativo no incluido). La web conserva sus funciones actuales. No se realizan consultas reales ni pagadas en CI.

## Diagnóstico breve para el chat

Ajustes → Diagnóstico → Copiar diagnóstico para el chat abre un resumen de hasta 6000 caracteres. Se puede copiar y pegar como mensaje, compartir como texto sin archivo o guardar como TXT breve. El TXT completo continúa disponible. El resumen incluye versión, Android API, periodo UTC, número de eventos y hasta 12 tipos recientes de fallo agrupados; no incluye mensajes, cuerpos, URLs, consultas IA ni credenciales. Las consultas SQL rutinarias ya no se escriben una por una. Los registros anteriores se leen sin borrarlos.

El registro sigue usando una cola limitada; cuenta los eventos omitidos por saturación y no descarta la barrera que espera las escrituras al exportar. Las reglas de redacción se reutilizan y cubren claves Gemini y credenciales JSON entre comillas. La revisión añade una segunda comprobación de cantidades al abrir una propuesta IA, con lectura actual de Room.

No corrige la carga de adjuntos de ChatGPT ni observa errores internos del juego. Si el resumen no muestra fallos, no garantiza que todos los flujos funcionen. Persisten las limitaciones de proveedores reales, datos comunitarios y pruebas físicas descritas arriba.

### Conexiones y meta
Guarda varias conexiones cifradas, selecciona un perfil y consulta los modelos disponibles desde IA → Conexiones.
La conexión anterior se conserva como perfil legado. No se incluyen claves en respaldos de colección.
Meta descarga una muestra pública de Limitless (máximo 12 torneos recientes, 30 días), conserva caché y muestra fecha/fuente.
Adaptar a mi colección abre el asistente sin consulta automática; los IDs fuera del catálogo bloquean la adaptación.
Las estadísticas son de esa muestra, no del juego completo. Ayuda y tutoriales está en Más.


Modelos y guías (2026-10-03): el listado aplica un filtro conservador de modelos de texto: Gemini requiere generateContent exacto y familia estándar con JSON; OpenAI excluye modelos especializados/solo Responses. Compatible requiere metadatos explícitos de endpoint y salida texto; cuando faltan, el usuario puede introducir el ID manualmente. No se afirma cuota, precio ni éxito de generación por listar modelos; no se hacen probes pagados. Los perfiles existentes no se cambian. Guía usa libro y texto en lugar de ?; Más muestra tarjetas en dos columnas inspiradas en las capturas del juego, y los iconos de Mazos/Más representan su función. Prueba adicional del filtro; revisión real de UI en teléfono pendiente.


Avatares de perfil (2026-10-03): seis personajes originales de estilo entrenador, recurso WebP incluido en el APK (sin descargas). Toca el avatar en Colección, elige y confirma Usar avatar; Cancelar conserva la elección previa. DataStore guarda un ID estable, cambiar tema no lo altera. Respaldo JSON incluye avatar; restaurar un respaldo antiguo sin ese campo conserva el avatar actual. IDs desconocidos vuelven al avatar predeterminado. Dos pruebas cubren persistencia/independencia del tema, restauración antigua/nueva y contenido del respaldo. La propuesta de rediseño completa es una referencia visual; este lote implementa el selector de avatares, no todo el rediseño. Verificación visual en teléfono pendiente. Atlas creado mediante generación de imagen con seis retratos originales en cuadrícula 3x2, convertido a WebP para reducir el APK.


Inicio (2026-10-03): resumen local de colección, copias y mazos; accesos a la colección, biblioteca, editor, IA, meta y guía. Muestra el avatar y el perfil/proveedor/modelo activos sin consultar la API. El mazo abierto se puede continuar sin reemplazarlo. Barra Inicio/Colección/Mazos/IA/Más; Meta está en Inicio y Más y se actualiza solo con su botón. La fecha de la muestra guardada se presenta en la zona horaria del teléfono.
