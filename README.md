# Demeberant TCG Pocket

Aplicación Android con Jetpack Compose, Room y un CSV de colección incluido.

## Compilar desde GitHub o desde un equipo

En **Actions → Validate Android and web → Run workflow** se ejecutan las pruebas,
Lint y la compilación debug. Los informes y el APK debug aparecen como artefactos
cuando la ejecución termina correctamente. El APK debug sirve para pruebas; no
es una actualización de una APK anterior firmada con otra clave.

Requisitos locales: Java 17, Android SDK 36 y Build Tools 36.0.0.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

La página web es una portada informativa; la aplicación web completa no existe
en este repositorio. La portada se puede comprobar con:

```sh
npm ci
npm run build
```

## Colección y datos

- La importación acepta CSV UTF-8 con coma o punto y coma, campos entre comillas,
  saltos de línea y comillas escapadas. Requiere Set, ID, Nombre, Rareza y Cantidad.
- Importar actualiza las cartas del archivo y conserva las cartas que no aparecen.
  Cantidades negativas, IDs inválidos y cartas duplicadas se rechazan antes de escribir.
- Los IDs se normalizan (`A1-1` → `A1-001`), respetando conjuntos como `PROMO-A`.
- El CSV se exporta a un archivo elegido por el usuario. No incluye mazos guardados
  ni preferencias, por lo que no representa un respaldo completo de la aplicación.
- La migración de la base de datos 3 → 4 conserva la colección y los mazos.
  Los alias se fusionan conservando la mayor cantidad y las marcas de deseadas.
  No se suman para evitar duplicar cantidades de la misma carta importada.
- No hay borrado automático si aparece un esquema anterior sin migración conocida.
  Para versiones 1/2 se necesita su esquema original antes de añadir una migración segura.

## Alcance de las recomendaciones

El catálogo estático A1 es parcial y necesita contrastarse con datos oficiales.
Las cartas importadas conservan nombre y rareza; no se inventan PS, ataques,
tipo ni sobre de procedencia. Las cartas sin sobre verificado se excluyen del
recomendador. El porcentaje de colección mide el catálogo disponible, no todas
las cartas existentes en el juego.

El generador limita a dos copias por nombre (incluidas variantes), respeta las
cantidades del inventario y comprueba las evoluciones de las plantillas conocidas.
Muestra advertencias y evita guardar propuestas incompletas o con datos sin
verificar. No valida todas las reglas y cartas de todas las expansiones.

El análisis es local y utiliza plantillas A1 históricas. No llama a Gemini ni
comprueba el meta actual. La calculadora usa las tasas orientativas heredadas;
no deben interpretarse como tasas oficiales vigentes ni garantías. Se retiraron
los porcentajes fijos del recomendador que no dependían de las cartas objetivo.

Los temas principales siguen los colores de MaterialTheme. El selector de idioma
cambia las imágenes; la interfaz permanece en español. La disponibilidad de las
imágenes depende del proveedor externo.
