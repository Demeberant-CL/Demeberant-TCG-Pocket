# Demeberant TCG Pocket

Aplicación Android y web para gestionar una colección de Pokémon TCG Pocket.

## Funciones

- Catálogo comunitario local de 4317 cartas y 23 expansiones, revisión del 01-10-2026.
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
