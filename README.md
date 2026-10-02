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
- No hay integración real con Gemini ni resultados de IA simulados.
- No se incorporan tasas de apertura no verificadas. La calculadora no modela la garantía
  de Pokémon básico en la mano inicial, habilidades ni efectos.
- La web compilada se entrega como artefacto; no se despliega ni se fusiona en main automáticamente.

Fuentes y licencias: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Módulo avanzado: IA, sandbox, efectos y diagnóstico

En Análisis hay tres nuevas secciones: IA, Sandbox y Efectos.
IA utiliza el servidor de `backend/` para conectar a OpenAI Responses API con JSON Schema estricto.
La app solicita confirmación antes de enviar contexto y solo abre propuestas validadas como borradores.
La clave API permanece en el servidor; URL y token de acceso se introducen en la pantalla IA.
El token no se persiste, no se exporta en respaldos ni se registra. Para configurar:
[backend/README.md](backend/README.md). No se ha desplegado ese servidor.

Sandbox practica con el mazo abierto de 20 cartas: mano, activo, banca de tres, descartes,
robo, turnos, marcadores de daño/energía y deshacer. Es un tablero manual de un jugador,
sin ejecución automática de ataques ni reglas. La garantía inicial de básico usa un
intercambio de carta como aproximación explícita. No modifica cantidades ni mazos guardados.

Efectos indexa hasta 25 cartas nuevas por solicitud y consulta Room por PS, tipo,
texto de ataques/habilidades y roles estimados (robo, curación, energía, milling, movilidad).
Una carta se indexa también al abrir sus detalles. Las etiquetas son heurísticas y
los resultados solo incluyen cartas con reglas disponibles en el idioma seleccionado.
La migración Room 4→5 crea la caché de reglas conservando colección y mazos.

Diagnóstico: logs diarios con escritura asíncrona, cola acotada, rotación y máximo de seis
archivos de aproximadamente 1 MB. Registra red (operación/HTTP/tiempo/tamaño), SQL sin
parámetros, errores de Room y crashes no capturados antes de delegar al manejador Android.
No conserva cuerpos privados ni credenciales. Exportación TXT por FileProvider desde Ajustes;
el selector de compartir lo abre el usuario, sin enviar automáticamente.

Capas: `data/` (Room, HTTP y repositorios), `domain/` (validadores, roles y sandbox),
`ui/` (Presentation: ViewModels y Compose). ZXing no está instalado en esta revisión;
este módulo no incorpora códigos QR, draft, giroscopio ni estadísticas de torneos.

## Configuración simplificada
Android utiliza español fijo para interfaz, imágenes y detalles. Configuración no guarda idioma.
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
