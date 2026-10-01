# Firma estable para versiones de prueba

Android no permite actualizar una aplicación con una firma incompatible.
La compilación anterior generaba una clave debug temporal en cada runner.
Desde esta corrección, las pruebas siguen funcionando sin secretos, pero el
APK solo se publica en android-apk cuando se restaura una clave fija desde
ANDROID_DEBUG_KEYSTORE_BASE64. android-reports contiene únicamente informes.

La clave usa el formato debug estándar: alias androiddebugkey, contraseña de
almacén y de clave android. Es una firma para pruebas privadas, no para publicar
en Play Store. La clave privada debe conservarse como secreto; no se sube al
repositorio, a informes ni a artefactos. No hay alternativa automática que
genere otra clave cuando falta el secreto. La huella pública acompaña al APK.
El versionCode de CI aumenta usando 1000 + GITHUB_RUN_NUMBER.

## Configuración pendiente

Crear o recuperar un debug.keystore compatible y guardar su contenido base64
como secreto de Actions ANDROID_DEBUG_KEYSTORE_BASE64 en:
https://github.com/Demeberant-CL/Demeberant-TCG-Pocket/settings/secrets/actions

La integración disponible no permite gestionar secretos del repositorio.
Si existe la clave anterior, reutilizarla. Una clave nueva estabiliza versiones
futuras, pero no actualiza una aplicación ya instalada con otra firma.
Un APK contiene el certificado público; no permite extraer la clave privada.

Si la clave instalada se perdió, exportar y verificar primero los datos.
El CSV existente conserva la colección y marcas admitidas por su exportador,
pero no constituye un respaldo completo de mazos y ajustes. No desinstalar
ni borrar datos hasta contar con una migración verificada. No se cambia el
applicationId ni se borran datos para eludir este conflicto.

## Validación

Pendiente la ejecución CI de este cambio. La ruta con secreto requiere una
clave configurada y comprobar la instalación de dos APK sucesivos en el mismo
dispositivo. El éxito de pruebas, Lint y compilación no confirma compatibilidad
con la firma de una app ya instalada.
