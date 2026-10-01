# Validación de las correcciones

Base remota verificada: `e2b77e420d120cf6f5fcf57fe4975f65846cb5a0`.
Rama local: `fix/collection-decks-validation-20261001`.
El parche adjunto se aplicó íntegramente, sin conflictos. No había AGENTS.md ni
README en la revisión base. Los CSV incluidos permanecen idénticos a la base.

Validación repetida el 1 de octubre de 2026 (UTC):

- `testDebugUnitTest`: 15 pruebas aprobadas, ninguna omitida ni fallida.
- `lintDebug`: aprobado, 0 errores y 26 advertencias.
- `assembleDebug`: aprobado, APK debug generado.
- `npm ci` y `npm run build`: aprobados.
- Revisión de whitespace aprobada con `core.whitespace=cr-at-eol` para el
  wrapper oficial de Windows, que usa CRLF.

La validación Android utilizó Gradle 9.3.1, un JDK completo y Android SDK 36
disponibles localmente, en modo offline. 23 de las 56 tareas usaron caché de
compilación; las pruebas y Lint se ejecutaron en esta sesión. El wrapper no
pudo descargar Gradle por restricción de red, por eso se utilizó la misma
versión instalada. No se probó en dispositivo físico.

## Publicación

El acceso de escritura se restableció después de instalar el conector en la
cuenta propietaria Demeberant-CL. Los cambios se publican en una rama separada
para revisión mediante pull request; main permanece sin modificar.

## Limitaciones pendientes

Integración real con Gemini, catálogo completo y probabilidades oficiales
vigentes pendientes. La web es una portada informativa. La interfaz continúa
en español aunque se cambie el idioma de las imágenes. Las migraciones desde
BD 1/2 necesitan sus esquemas originales; no hay borrado automático como
alternativa. La migración 3→4 conserva cantidades y marcas fusionando alias
con la cantidad máxima, sin sumarlas. El APK debug utiliza firma de pruebas.
