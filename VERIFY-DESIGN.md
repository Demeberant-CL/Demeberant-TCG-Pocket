# Diseño TCG Dex

La paleta azul nocturna, cian y violeta se aplica mediante el tema compartido a Inicio, Colección, Mazos, IA, herramientas, ajustes y diálogos. El visor nativo de sincronización usa la misma paleta. La actualización selecciona este tema una sola vez; las elecciones posteriores de tema siguen guardándose.

Inicio muestra el nick, el nivel y el ID de la cuenta sincronizada. El ID conserva sus 16 dígitos y se puede copiar. Nick y nivel se leen únicamente de elementos visibles del perfil; si no están disponibles, pueden completarse con el lápiz. No se usan datos ficticios en la app. Los ejemplos de las capturas pertenecen exclusivamente al emulador de pruebas.

La fecha y las novedades se registran tras una sincronización guardada correctamente. Las novedades comparan las cantidades con las almacenadas antes de esa operación. Repetirla sin cambios produce cero novedades. Cancelar o fallar no actualiza ese resumen.

Las portadas usan las imágenes reales del catálogo. En el editor, «Usar como portada» selecciona una carta del mazo; guardar conserva la selección. Los mazos antiguos obtienen una portada de sus propias cartas. La selección se incluye en el respaldo sin cambiar el esquema de la base de datos.

Validación: pruebas Android de datos, preferencias, respaldos e importación; pruebas web de compatibilidad; fixtures del recorrido y de la lectura del perfil; comprobación de navegación y capturas Android con tamaño de texto normal y ampliado. La lectura del nick y nivel desde la cuenta real todavía debe comprobarse en el teléfono.
