# Pocket · Importación de prueba 0.1.1

Independent copy of the main Android app, developed only on the `experiment/pocket-zone-import/20261008` branch. No changes are merged into `main` or another experiment. Package: `cl.demeberant.pocketzone.importtest`; its private database, preferences and FileProvider are distinct from the installed main app and Pocket Zone. The clone does not restore Android cloud backups automatically. Updates use the repository's existing persistent CI signing key and increasing version codes.

## Phone test

1. Install the APK as a separate app. Do not uninstall the main app or Pocket Zone.
2. Open **Pocket · Importación de prueba** and tap **Importar JSON de Pocket Zone**.
3. Choose `zone-vista-previa-….json` directly from **Descargas**. Select the JSON, not the diagnostics ZIP. If Android's Recientes provider exposes 0 bytes, select the file through the Downloads location.
4. Review the player and counts. Tap **Importar en esta copia**, then **Volver a la colección**. In Colección, choose the owned-card filter.
5. Reimport the same file. Quantities must stay unchanged. Close and reopen the app; they must remain saved.
6. Mark a card as wanted, import again and check that its wanted flag stays set. Saved decks and absent cards also stay unchanged.
7. **Copiar resultado para el chat** provides a short diagnostic without exporting the collection or player identifier.

For the user's verified complete reference, the expected import is 1,328 distinct card IDs, 2,414 copies and 24 sets. This personal reference is not stored in this repository. Automated full-size tests use the public catalog with synthetic quantities.

## Input contract

Only the actual Pocket Zone export schema is currently supported: `schemaVersion: 1`, `source: "pokemon-zone"`, 16-digit string `friendId`, matching HTTPS player-cards `url`, ISO-8601 `readAt`, boolean `collectionComplete`, and nonempty `visibleCards` array. Each row needs `cardPath` (`/cards/<set>/<number>/<slug>/`) and integer `quantity` in 0–99,999. Limit: 3 MB UTF-8 / 10,000 input rows. Export metadata and display names are not treated as identity.

`A1/1` maps to `A1-001`; promo set hyphens are preserved (`promo-a/1` → `PROMO-A-001`). Different card numbers preserve art variants. Catalog metadata supplies the card name and rarity. Unknown IDs, invalid quantities, wrong origins/players, and contradictory duplicate quantities reject the whole import. Identical duplicate aliases collapse without adding copies. No static Zone internal-ID table is used.

The actual exported file currently says `collectionComplete: false`, even when the user has confirmed it contains every owned card. The importer displays this technical limitation and replaces only supplied quantities; it never zeroes missing cards. It does not independently contact Nintendo to confirm completeness. A future explicitly confirmed full-replacement mode is outside this test.

## Persistence and limits

Uses the app's existing Room repository and schema 5, with a wrapping transaction and read-back verification of quantities in both inventory and user-card tables. A failed transaction rolls back. Reimporting replaces quantities; no additive import. Existing wishes, acquisition dates, absent cards, saved decks and settings are preserved. Selection/validation/import state survives rotation through a ViewModel; interruption by process termination relies on SQLite atomicity and may require selecting the file again. Cancellation can discard uncommitted work; it cannot undo an already committed transaction. All JSON processing is local. The app's existing optional network features remain present, but the importer itself sends no data.

This build tests JSON-to-collection integration only. The Pocket Zone WebView and one-button Sync-and-read are not yet embedded in this clone. No direct access to another app's private storage, Google/Nintendo session, or account tokens is used.

## Validation

Run `./gradlew testDebugUnitTest lintDebug assembleDebug`. New tests cover actual schema parsing, promo IDs/art variants, duplicate aliases/conflicts, strict quantities, wrong sources/players/URLs, oversize input, unknown IDs, full-size synthetic imports, idempotence, preserved wishes/decks/absent cards, and forced second-table failure rollback. CI checks the isolated package ID and persistent signing certificate before publishing the APK.
