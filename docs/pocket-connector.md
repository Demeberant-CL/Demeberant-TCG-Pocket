# Conector Pocket Atlas ↔ chat

Implementación independiente de la API de generación. El servidor no llama a OpenAI, Gemini ni otro modelo. No convierte la IA local en un modelo conversacional ni incrusta este hilo dentro del APK.

## Flujo disponible

1. Ajustes → Conector de chat: guardar origen HTTPS y clave del dispositivo.
2. Sincronizar: comparte catálogo, cantidades, lista de deseos, efectos ya descargados, mazos guardados y borrador. Los efectos sin verificar se identifican con `effectsKnown=false`. Se sustituye la copia anterior y se vacía el buzón de propuestas anteriores.
3. En un cliente MCP configurar `https://TU-SERVIDOR/mcp` con OAuth. El propietario autoriza utilizando la clave del propietario, diferente de la clave Android. Esta clave no debe colocarse en la app ni enviarse en el chat.
4. El chat puede consultar `collection_summary`, `search_cards` (100 filas por página) y `read_deck`.
5. `submit_deck_proposal` deposita una propuesta de 20 cartas ligada a la revisión consultada. No altera inventario ni mazos guardados. El servidor comprueba IDs, cantidades, energías y máximo dos por nombre; Android valida también estructura y evoluciones.
6. Recibir propuestas → revisar lista y estrategia → Abrir como borrador → Mazos. La colección actual se comprueba nuevamente al abrir.

ChatGPT requiere que la cuenta o espacio tenga habilitados conectores MCP personalizados. El protocolo y OAuth se prueban localmente; conexión real a una cuenta ChatGPT pendiente de despliegue y registro. No se ha verificado soporte del mismo conector en Gemini de consumo. No hay actualización automática en segundo plano ni chat embebido: sincronización y recepción son acciones explícitas.

## Servidor

Node 22+, sin paquetes adicionales:

```bash
node --test backend/*.test.mjs
```

Variables de entorno del alojamiento:

| Variable | Valor |
|---|---|
| `POCKET_PUBLIC_URL` | Origen público HTTPS sin ruta, por ejemplo `https://atlas.example.com` |
| `POCKET_DEVICE_TOKEN` | Secreto aleatorio de al menos 32 caracteres, exclusivo del dispositivo |
| `POCKET_OWNER_SECRET` | Otro secreto aleatorio de al menos 32 caracteres para autorizar OAuth |
| `POCKET_STATE_FILE` | Archivo en volumen persistente, por ejemplo `/app/data/pocket.json` |
| `PORT` | Puerto HTTP interno, predeterminado 8080 |
| `HOST` | 127.0.0.1 por defecto; 0.0.0.0 en contenedor detrás de HTTPS |

Generar cada secreto por separado con `node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"`. Configurarlos como secretos del alojamiento. No incluirlos en Git, imágenes de contenedor, APK o mensajes.

Construcción desde la raíz del repositorio:

```bash
docker build -f backend/Dockerfile.connector -t pocket-connector .
```

El alojamiento debe proporcionar HTTPS y un volumen persistente de propietario `node` para `/app/data`. Health check `/health`. Inicio sin Docker: `node backend/connector.mjs`. No exponer el puerto HTTP interno directamente a Internet. Los tokens y códigos OAuth se mantienen solo en memoria: al reiniciar se requiere reconectar el chat. Los clientes registrados llevan metadatos autenticados y sobreviven a reinicios mientras se conserve la clave del propietario. Los refresh tokens caducan a las 24 horas; access tokens a una hora; códigos a cinco minutos. Para revocar conexiones inmediatamente, reiniciar el proceso. Servidor de un solo propietario y una sola instancia: no usar el mismo volumen con varias réplicas.

Los datos se guardan en un archivo local del servidor con permisos 0600. El cifrado del volumen y sus copias depende del alojamiento. Eliminar datos del servidor elimina la copia activa y el buzón; no borra registros del chat ni respaldos del proveedor de alojamiento. El teléfono guarda su configuración cifrada con Android Keystore, fuera de respaldos de colección y Android.

## Validación y límites

Pruebas de autenticación, OAuth PKCE de un solo uso, permisos de solo lectura, límites de cantidades, IDs falsos, revisión antigua, duplicados por nombre, persistencia, eliminación y Origin. Los efectos desconocidos no se generan: hay que descargarlos en la app o usar fuentes del juego. Una propuesta válida estructuralmente no garantiza calidad competitiva. El alojamiento puede tener coste aunque no se utilice API de IA.

Documentación de referencia:
- https://developers.openai.com/plugins/deploy/connect-chatgpt
- https://developers.openai.com/plugins/build/auth
- https://modelcontextprotocol.io/specification/2025-11-25/basic/transports
