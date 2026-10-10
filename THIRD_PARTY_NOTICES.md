# Fuentes de datos

Catálogo comunitario de 4317 cartas y 24 colecciones (incluidas promociones), consultado el 1 de octubre de 2026.
Fuente: https://github.com/flibustier/pokemon-tcg-pocket-database
Revisión: 68dcb17474ecff17f1ecd791975c4e4f0babb0be

Se utilizan identificadores, nombres, rarezas, sobres y parte de los metadatos de evolución.
No se importan los PS constantes, daño, retirada ni tasas de apertura del fichero extra.
Los datos comunitarios pueden tener errores y no son una publicación oficial de Pokémon.
Los detalles se consultan opcionalmente a TCGdex: https://tcgdex.dev/rest/card
Las imágenes y marcas pertenecen a sus respectivos titulares.

MIT License

Copyright (c) 2025 Jon (flibustier)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


# QR de mazos

Formato binario adaptado de https://github.com/KevinGutowski/tcgp-deck-qr
Revisión 102d75e45ad244099d22ea5beba7bba57ad51be2; docs/format.md, src/payload.js y fixture conocido test/payload.test.js.
Mapa de 4317 impresiones derivado del nombre de imagen del catálogo comunitario de la revisión indicada arriba. Los IDs internos son entidades semánticas; artes distintos pueden compartir entidad.

MIT License

Copyright (c) 2026 Kevin Gutowski

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

ZXing Core 3.5.3: https://github.com/zxing/zxing — Apache License 2.0. Generación/lectura QR en pruebas; no acceso de cámara.

## PocketDecks combat supplement

`app/src/main/assets/pocket-combat-extra.json` is a transformed subset of
PocketDecks/pokemon-tcg-pocket-cards `data/v5/cards.gameplay.no-image.min.json`,
revision `ec444f849be468d0104977906b4e1f14b42a5d47`, retrieved 2026-10-10.
Version 5 datasets are AGPL-3.0-or-later (not the MIT legacy dataset).
The complete license is included in `app/src/main/assets/PocketDecks-AGPL.txt`.
Corresponding transformed data and integration source are provided in this
public repository; upstream: https://github.com/PocketDecks/pokemon-tcg-pocket-cards
Copyright (C) 2026 Leonid Dalin and Chase Manning.
Pokémon names, text and artwork remain owned by their respective rights holders.
