# Objetivos e intercambios: revisión del 11 de octubre de 2026

## Entrega
- Asesor accesible desde Inicio y Más → Canjes. Ofrece parejas concretas de igual rareza con motivo, copias restantes y copia de propuesta.
- Objetivo por lista guardada, lista de torneo o expansión; Deseos aumenta prioridad. Las listas de torneo se ordenan opcionalmente por faltantes.
- Reserva de una o dos copias, bloqueo manual y protección del máximo de copias exactas necesarias entre mazos. No se suman las reservas de distintos mazos. Una lista ilegible impide ofrecer cartas hasta su revisión.
- El CSV de otra persona debe contener un cero explícito para identificar un faltante y sobrantes por encima de la reserva para recibirlo. Un archivo omitido o incompleto no inventa coincidencias.
- Inicio compacto con objetivos, intercambios, disponibilidad de listas y acciones. Dos diseños nuevos: Centro de decisiones y Álbum. Cambian orden, portadas y vista inicial de Colección; todos conservan acceso a mazos y herramientas.
- Objetivos y protección persistentes en DataStore y respaldo; respaldos antiguos usan valores por defecto.
- Sobres usa el objetivo del mazo y la expansión, y distingue segundas copias faltantes. Es cobertura, no probabilidad ni comparación de costes.

## Límites explícitos
No existe una fuente incorporada y verificada de elegibilidad/coste vigente por carta. La pantalla pide comprobar elegibilidad, marco, flair, idioma y coste en el juego. Se excluyen promociones, inmersivas, coronas y brillos como candidatos del asesor; la lista restante no se afirma elegible. No se ejecutan intercambios ni se descuenta inventario. Opciones sin CSV no afirman disponibilidad de contraparte; cada pareja se evalúa por separado. No se usa precio monetario ni rareza como prueba de rentabilidad.

La protección y los objetivos usan versiones exactas; no se supone equivalencia funcional por nombre. Pendientes: equivalencia validada por efectos, historial confirmado, tasas por ranura/puntos actualizados y simulación competitiva. El constructor local sigue siendo heurístico.

## Comprobación
Pruebas de reservas compartidas, bloqueo, mazos ilegibles, prioridades, rareza, CSV incompleto y respaldo. El emulador verifica copia de propuesta, persistencia de protección y capturas de los dos diseños con texto normal y ampliado, además del recorrido existente.
