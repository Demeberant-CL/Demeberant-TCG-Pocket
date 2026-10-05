# Pocket iOS nativo

SwiftUI, iOS 17+ provisional, SQLite y Keychain. Proyecto separado de Android, sin WebView ni datos personales precargados.

## Desde el celular
La rama `ios/native-port-20261005` contiene el proyecto `ios/Pocket.xcodeproj` y un workflow macOS independiente. GitHub Actions compila para simulador y ejecuta XCTest al actualizar la rama. Revisa el artefacto `ios-native-review`: contiene log, simuladores, resumen y capturas. El resultado XCTest completo está en `ios-native-xcresult`. Una ejecución de CI no instala automáticamente la app en tu iPhone.

En Mac: abre `Pocket.xcodeproj`, elige esquema Pocket y un iPhone con iOS 17+. Para comprobar todo ejecuta `bash ios/scripts/validate-macos.sh` desde la raíz del repositorio. El script registra Xcode y SDK reales y elige un simulador disponible. No precisa XcodeGen, CocoaPods ni paquetes externos.

## Instalar en un iPhone
Selecciona tu cuenta Apple/Team en Signing & Capabilities y confirma un Bundle ID disponible. `cl.demeberant.pocket.ios` es provisional. Esta entrega no contiene certificados, Team ID, IPA firmado ni TestFlight. Android conserva su identidad y firma originales.

## Colección actual
La app empieza vacía. En Android exporta el respaldo completo JSON v1; en iOS usa Más → Ajustes y respaldos → Importar respaldo JSON. Revisa el resumen y confirma. La importación mezcla registros y deduplica mazos por nombre, cards serializado y estrategia, igual que Android. Las claves se configuran nuevamente. El CSV histórico nunca se carga en producción.

## Datos y privacidad
SQLite guarda un documento versionado con colección, mazos, preferencias, borrador, perfiles sin claves y meta. Transacciones WAL y synchronous FULL confirman todos los dominios a la vez; un documento anterior permite recuperar fallos de decodificación. La memoria observable cambia solo tras COMMIT. SQLite es estable y permite validar el contrato antes de una escritura, sin depender de migraciones automáticas SwiftData. Las claves están en Keychain con acceso ThisDeviceOnly. La exportación Android v1 excluye claves, perfiles y borrador.

Los cambios de texto del editor se guardan tras 300 ms sin escribir; cantidades, cartas y energías se confirman inmediatamente. Cerrar conserva el borrador; crear otro requiere confirmación. El último intervalo de texto en curso puede perderse ante terminación abrupta antes de su confirmación.

QR usa payload binario Android, versión 9/H byte-mode sin ECI y zona blanca de 4 módulos. Prueba máscaras 0–7 y entrega solo imágenes que Core Image decodifique al payload exacto, incluido el PNG. La máscara principal no tiene por qué coincidir con la elegida por ZXing Android. No se afirma aceptación universal por el juego.

Consulta `PARIDAD.md` y `validation/` para conocer qué está implementado, comprobado y pendiente. No se afirma que las 106 pruebas Android sean pruebas iOS aprobadas.
