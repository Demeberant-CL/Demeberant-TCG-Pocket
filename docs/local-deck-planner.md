# Constructor local de POCKET ATLAS

La creación principal usa la colección en el dispositivo, sin peticiones de IA ni cuotas. Presenta hasta tres propuestas diferentes, selección automática de energía o un tipo, estilos Equilibrado/Rápido/Resistente y un Pokémon principal opcional. Abrir una propuesta requiere conservar o reemplazar explícitamente cualquier borrador con cambios; se comprueban las cantidades otra vez antes de aplicarla.

## Motor

Compara núcleos completos de evolución y añade paquetes de Pokémon o entrenadores según valor incremental: costes reales de ataques, daño impreso (descontando cifras variables), PS, retirada, robo/búsqueda, aceleración, concentración de básicos y diversidad de energías. Prefiere mazos completos, conserva los límites por nombre entre ilustraciones y nunca fabrica cartas faltantes. Los fósiles requieren su carta anterior; las evoluciones desconocidas no se convierten en básicos. La búsqueda acotada es determinista y se ejecuta fuera del hilo de UI.

Las puntuaciones son heurísticas internas, no probabilidades de victoria. No simula todos los efectos, condiciones de juego ni emparejamientos. Las cartas sin costes/efectos se señalan como valoración parcial; los mazos incompletos siguen siendo borradores. No usa rareza ni ilustraciones como medida de fuerza.

## Datos

`pocket-combat.json` incluye 2480 cartas coincidentes con el catálogo, de `TCGdex/cards-database`, revisión `4199850a6af49665db0080fa2bb9ef751750a406`. Datos de ataques presentes en 2283 cartas. Se conserva la licencia MIT en `TCGdex-LICENSE.txt`. Los detalles ya guardados en el dispositivo complementan los datos incluidos. No se necesita descargar reglas para obtener propuestas.

Se descartaron los campos de combate de `flibustier/.../cards.extra.json`: las 2211 cartas con salud tenían exactamente el valor 50; no aportaban ataques ni costes. Esa fuente sigue sirviendo al catálogo original, pero esos PS no se usan para puntuar.

Reglas de construcción contrastadas con Pokémon.com:
https://www.pokemon.com/es/estrategia/aprende-a-crear-una-baraja-en-jcc-pokemon-pocket

## Comprobación

Pruebas de inventario y límites entre impresiones, líneas completas, carta principal seleccionada, ataques compatibles, energía Dragón explícita, entrenadores de energía incompatibles, datos faltantes, determinismo y datos incluidos reales. El recorrido Android genera propuestas sin configurar IA, examina cartas, abre el editor y verifica las escalas normal y 1.4.

## Offline data trial · 2026-10-10

Adds 3181 directly matched gameplay records from PocketDecks v5 (AGPL-3.0-or-later).
1424 card IDs were absent from the existing combat bundle. IDs and English names
must both match the bundled catalog; promotional identifiers are normalized.
The compact supplement adds 657708 uncompressed bytes. Existing Spanish effects
are retained. Data is read lazily on the IO dispatcher and reused in memory;
no provider keys or network request are required by the constructor.

Expanded proposals show the exact probability of collecting every core card-name
group among 8 uniformly drawn cards from 20, using inclusion-exclusion. This is
a theoretical sampling metric, not the actual opening-hand algorithm, a turn
prediction, or a win rate. It excludes guaranteed Basic selection, searches,
extra draws and evolution timing. Incomplete decks do not receive a metric.

The shared image loader uses a persistent, evictable 256 MiB disk cache. Images
are fetched as requested; this does not package every card image or increase
the memory cache. Offline display requires a previously successful cached load.

This first trial does not add a tournament template snapshot or mass simulations.
Performance on the user's phone is still to be measured, not guaranteed by APK size.
