# Pocket Zone · Pruebas

Prototipo Android independiente, dentro de `experiments/pocket-zone/`.
Su proyecto Gradle no está incluido en el proyecto principal. No modifica Android, web,
colección, mazos, preferencias, credenciales ni firma de TCG Pocket.

## Uso desde el teléfono

1. Instala el APK `pocket-zone-pruebas-apk` de su ejecución en GitHub Actions.
   Tiene otro icono, nombre e identificador y puede coexistir con TCG Pocket.
2. Pega el ID de amigo (10–20 números) o el enlace HTTPS de tu perfil de Pokémon Zone.
3. Toca Abrir perfil, espera a que se vean los datos y pulsa Leer datos visibles.
4. Revisa la vista previa. Ver cartas abre la sección de cartas del mismo perfil;
   puedes cargar más filas manualmente antes de volver a leer.
5. Guardar JSON permite elegir una ubicación mediante el selector de Android.
   El JSON es de diagnóstico: **no es un respaldo para restaurar en TCG Pocket**.

## Alcance y límites

- Solo perfiles públicos. No automatiza login, no recibe claves Nintendo, no sincroniza
  desde el juego y no sortea bloqueos del sitio. Abrir el navegador externo no traslada
  su sesión a este WebView.
- El ID se conserva como texto, evitando pérdida de precisión. Encabezado y título de
  página se muestran literalmente, sin inferir un nombre de jugador.
- Lee pares `dt/dd`, texto visible (máximo 6000 caracteres) y hasta 200 filas de cartas
  ya cargadas. Las filas no se equiparan aún a IDs del catálogo principal.
- Toda lectura se marca como colección incompleta. No se convierten ausencias a cero,
  ni se importa o escribe ninguna colección. Los selectores de cartas pueden necesitar
  ajustes si el sitio cambia. No se ha demostrado lectura real del perfil del usuario.
- Las imágenes de la página están desactivadas para reducir consumo; el resto de recursos
  web del sitio puede descargarse. Solo HTTPS; navegación principal restringida al perfil
  y su página de cartas; sin puentes de JavaScript a funciones Android.
- El enlace se guarda en preferencias propias; el JSON solo se guarda al solicitarlo.
  Borrar datos de esta prueba limpia sus preferencias, cookies y almacenamiento WebView.
  Los datos de otra app quedan separados por el identificador Android.
- Al girar el teléfono se vuelve al inicio conservando el enlace; una vista previa sin
  exportar no se conserva. Una exportación en curso conserva su contenido validado.
- Respaldo Android deshabilitado. No se solicitan permisos de archivos, contactos,
  cuentas o acceso a otras apps; únicamente INTERNET.

## Compilación y validación

Desde la raíz del repositorio:

```bash
./gradlew -p experiments/pocket-zone testDebugUnitTest lintDebug assembleDebug --no-daemon
python3 experiments/pocket-zone/check-isolation.py --apk-metadata
node --test experiments/pocket-zone/extract.test.cjs
```

Las pruebas DOM usan Playwright y páginas de prueba locales interceptadas: no realizan
peticiones a Pokémon Zone. Se comprueban carga pendiente, bloqueo, origen, texto oculto,
campos sensibles, límites y cartas visibles. Las pruebas Java comprueban origen, ID,
schema, cantidades, límites y descarte de campos desconocidos. No sustituyen una prueba
real en el teléfono, con su perfil público, conectividad y tamaño de fuente.

El workflow separado `pocket-zone.yml` compila únicamente este proyecto en ramas
`experiment/pocket-zone/**`. No utiliza ni cambia la clave de firma principal.
La clave debug experimental se conserva en una caché propia de Actions; si esa caché
caduca, una actualización podría requerir desinstalar solo Pocket Zone · Pruebas.
El código continúa disponible en la rama; no se fusiona con main ni con el PR #1.

## Base conservada

TCG Pocket 1.0.1144, commit `9da0780c1b0ac033144dfb8ead816a1720c55ef6`.
ID principal: `com.aistudio.tcgpocket2.kxmpzq`.
ID experimental: `cl.demeberant.pocketzone.experimental`.

Referencias de diseño: documentación Android WebView y su seguridad;
documentación pública de Pokémon Zone Collection Tracker. Los selectores de cartas
coinciden con el HTML descrito por el exportador comunitario de Ivan Donisete Lonel,
sin incorporar su código. Una API pública de perfil no ha sido verificada.

## Registro del proceso (actualización)

Desde Inicio o la barra superior: **Iniciar registro nuevo**, reproduce los pasos,
**Detener registro** y **Guardar diagnóstico ZIP**. El ZIP contiene `registro.json`
y `resumen.txt` en UTF-8, listo para adjuntar al chat. No se envía automáticamente.

El registro guarda hasta 400 eventos recientes y el número de eventos omitidos:
navegación, peticiones con método, errores HTTP/red, inicio/resultado de lectura y
algunas pulsaciones reconocidas (Sync, acceso a cuenta y cargar más). Las rutas usan
una lista cerrada de palabras; IDs y segmentos desconocidos se sustituyen por
`{hidden}`. No guarda valores de consultas, fragmentos, URLs originales, dominios
externos, cuerpos, cabeceras, cookies, tokens, mensajes de error ni formularios.
Las pulsaciones se observan únicamente tras cargar la página y durante el registro;
una navegación inmediata puede impedir recoger una pulsación. No se registra todo.

**Mi cuenta** permite navegar manualmente en el mismo dominio HTTPS de Pokémon Zone.
La lectura de perfil continúa limitada a su página y cartas, sin extracción en login
o ajustes. Puedes usar el acceso propio del sitio por usuario y contraseña; Google,
Discord y Nintendo externos continúan bloqueados en este WebView. No se capturan sus
flujos en otro navegador ni se transfieren sesiones. Nunca se pulsa Sync automáticamente.
Si lo pulsas manualmente, Zone advierte que puede cerrar la sesión del juego.

El diagnóstico se conserva solo en almacenamiento privado de esta app al detener
el registro, exportarlo o salir de la pantalla/app. Si el proceso falla antes, pueden
perderse los últimos eventos. Al reiniciar/girar el teléfono el registro queda detenido;
Iniciar registro nuevo sustituye la captura anterior. Borrar datos de esta prueba
limpia el diagnóstico. No modifica TCG Pocket, main ni el PR #1.
