# Conexiones IA y cambio rápido

Groq, OpenRouter, Mistral y DeepSeek se añaden a Gemini, OpenAI y la URL compatible personalizada. Cada perfil guarda su clave, modelo y nombre de manera independiente, cifrada fuera de los respaldos. Los nuevos proveedores usan URLs fijas: una URL antigua del formulario no puede desviar su clave. El modelo se descubre desde la cuenta o se introduce manualmente; no se supone cuota disponible.

Cambiar IA aparece en el chat y el asistente al guardar al menos dos perfiles. Seleccionar otro perfil no envía ninguna consulta, conserva el texto y permite reintentar la pregunta pendiente con la conexión nueva. Se descartan sugerencias pendientes del proveedor anterior. No existe cambio automático tras un error o cuota agotada.

Se conserva el formato de Gemini/OpenAI y las conexiones compatibles existentes. Groq usa max_completion_tokens; Mistral, DeepSeek y OpenRouter usan max_tokens. Las listas de modelos se filtran por capacidades publicadas; nunca prueban facturación ni generación por sí mismas.

Documentación contrastada:
- https://console.groq.com/docs/openai
- https://console.groq.com/docs/api-reference
- https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties
- https://docs.mistral.ai/api/endpoint/chat
- https://api-docs.deepseek.com/api/create-chat-completion/

Verificación: solicitudes interceptadas para comprobar destino, claves y formato; catálogos simulados con sus metadatos; persistencia y cambio entre perfiles nuevos y antiguos. El emulador cambia dos conexiones sin consultar APIs y comprueba que el texto permanece, con escala normal y 1.4. No se han usado claves reales ni comprobado cuotas de cuentas personales.
