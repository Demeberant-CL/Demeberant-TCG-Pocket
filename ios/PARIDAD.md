# Estado de paridad

Este documento registra código implementado; no sustituye una compilación ni una prueba en dispositivo. Actualizar resultados tras ejecutar CI.

| Área | Implementación | Verificación pendiente |
|---|---|---|
| Inicio y 5 destinos | Resumen real, avatar, recientes, borrador, fecha meta | Capturas, navegación, VoiceOver |
| Colección | Catálogo 4317, cantidades, deseos, filtros, búsqueda, lista/cuadrícula/grandes | Rendimiento y Dynamic Type 100/130/200% |
| Detalle | Imagen alta, cantidad conservada al girar, TCGdex y reglas, reintento | Red real, fuentes/imágenes y caché disco |
| Editor/biblioteca | Borrador, cartas, energías/notas, conteo, avisos, guardado y confirmaciones | Persistencia, recuperación y teclado en dispositivo |
| Android backup v1 | JSON anidado string, legacy codec, validación, merge, preview, export | Round-trip con instalación Android real |
| QR | Encoder binario, 4 fixtures, v9/H, 8 máscaras, margen 4, PNG degradado, Vision obligatorio | XCTest en simulador y aceptación real por juego |
| IA | 3 acciones, multiconexión, clave Keychain, confirmación, cancelación, validación colección/evolución | Model discovery, reemplazos con motivos, pruebas HTTP simuladas completas |
| Meta | Limitless POCKET, identidad, 12 torneos, límites 25/90 s, cancelación/caché, opt-in apertura | Red real y fixtures/red lenta |
| Sobres | Cobertura local con objetivos/deseos, sin tasas inventadas | Comparación visual Android |
| Canjes/CSV | Parser quoted/multiline/BOM, export, import preview, comparación local | Restricciones actuales del juego fuera de la app |
| Simulador | Tablero manual, básico inicial, zonas, energía/daño, deshacer y moneda | Gestos y sesión práctica en iPhone |
| Calculadora | Aperturas independientes e hipergeométrica | Interfaz con texto ampliado |
| Efectos | Cache reglas, heurísticas, palabra clave e indexar 25 | Filtros PS/elemento/idioma y persistencia ampliada |
| Avatares/tutoriales | Sprite original y 6 tutoriales separados | Recortes y legibilidad en dispositivo |
| Diagnóstico | Resumen sin información privada, TXT y ZIP compartibles | Compartir real y registro seguro de eventos adicionales |
| Proyecto/CI | Proyecto Xcode completo y script reproducible | Compilación Xcode y pruebas UI |

No se ha reducido el alcance ocultamente: faltan las extensiones enumeradas de IA, efectos, diagnósticos y validación; la entrega no debe presentarse como equivalencia completa hasta cerrarlas. No contiene IPA ni firma Apple.
