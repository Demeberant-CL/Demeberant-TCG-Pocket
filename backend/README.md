# Servidor del asistente

Node.js 22 o posterior. Sin dependencias adicionales.

Variables del **servidor**, nunca dentro del APK ni del repositorio:
- OPENAI_API_KEY: clave de la cuenta API con facturación habilitada.
- APP_ACCESS_TOKEN: token privado para acceder a este servidor.
- OPENAI_MODEL: opcional, gpt-4.1-mini por defecto.
- PORT: opcional, 8080 por defecto.

Ejecuta `node backend/assistant.mjs` y publica detrás de un proxy HTTPS.
El proceso escucha solo en localhost. La app requiere la URL HTTPS completa del endpoint
`/assist` y el token de acceso. El token se mantiene únicamente durante la sesión de Android.
No publiques la clave de OpenAI en la app. Una suscripción de ChatGPT no configura este servidor.

El endpoint autentica, limita tamaño y frecuencia, envía JSON Schema estricto a Responses API
con store:false, maneja negativas/respuestas incompletas y devuelve una propuesta.
Android valida otra vez IDs, cantidades, colección, básicos, evoluciones y sustituciones antes
de permitir abrirla en el editor. Ni la generación ni la apertura guarda/modifica colección.

Las consultas se hacen solo al pulsar un botón y confirmar el envío de contexto.
No hay scraping silencioso ni winrates inventados. El contexto meta se aporta manualmente.
El logger guarda identificador de petición, estado y tiempo, sin cuerpos ni credenciales.
Este servidor personal no sustituye autenticación multiusuario, cuotas por usuario ni supervisión
de costes para una distribución pública.

Validación: `node --test backend/assistant.test.mjs` (servicio OpenAI simulado, sin gasto).
No se ha desplegado este servidor ni se han realizado consultas pagadas en esta sesión.
