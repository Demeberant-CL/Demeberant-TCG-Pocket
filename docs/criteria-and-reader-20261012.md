# Visor, criterios y constructor

Basado en diagnostico-tcg-pocket (4).zip, versión 1.0.1309: generación local 17.815 ms; fallos 404/503 de detalles.

El lector abre con imagen acotada al 32% de altura, máximo 280 dp. Se retiran textos de resolución y gestos; ampliar sigue siendo opcional. Inicio elimina duplicaciones, prioriza expansiones incompletas, permite leer cartas de la vitrina y vuelve arriba al cambiar diseño. Navegación y mosaico de cuatro acciones se conservan.

Ambos constructores reciben criterios explícitos: colección o catálogo, expansiones, IDs obligatorios y excluidos. Se comprueba la combinación antes de generar y al validar; catálogo permite guardar una lista objetivo con faltantes sin modificar inventario. Los criterios por texto libre siguen dependiendo de la interpretación del proveedor; las selecciones explícitas se validan localmente.

La generación conectada prioriza cartas nombradas, obligatorias, borrador y preevoluciones antes de acotar 80 candidatos y 57 KB. Una respuesta sin propuesta indica cuándo el contexto es incompleto. La generación local usa candidatos acotados, caché de puntuaciones y clasificaciones reutilizadas. Buscar otras propuestas rota las mejores alternativas y filtra similitud por nombres. El motor sigue siendo heurístico y no se valida rendimiento competitivo. No puede garantizar variedad si hay pocas combinaciones.

Detalles intenta inglés ante 503 sin copia, conserva copia vieja disponible y utiliza datos locales ante fallo de red o HTTP. Las identidades incorrectas siguen fallando. Registro de eventos nuevos incluye versión, tiempo y tamaños de contexto, sin texto de consultas ni credenciales. Resumen separa eventos versionados e histórico desconocido.

Pruebas añadidas: restricciones de expansión y catálogo, IDs exactos obligatorios, fallback 503 sin copia, separación de versiones. Recorrido visual con letra normal y ampliada.
