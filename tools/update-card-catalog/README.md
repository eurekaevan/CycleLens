# CycleLens card catalog updater

This development-only tool reads both `items` and `supportItems` from the official
Clash Royale `/v1/cards` response, matches them against reviewed CycleLens
overrides, and reports all unknown, stale, or unmapped visual objects before any
production file can change.

Dry-run is the default:

```bash
CLASH_ROYALE_API_TOKEN=... python3 tools/update-card-catalog/catalog_updater.py
```

After reviewing and updating `card-overrides.json`, explicitly write the catalog
and original PNG bytes with:

```bash
CLASH_ROYALE_API_TOKEN=... python3 tools/update-card-catalog/catalog_updater.py --update
```

For an audited saved API response, `--response-file path/to/cards-response.json`
can be used without a token. The tool never prints the token. It refuses update
mode while an upstream object is unmapped or an included local object is stale.
An upstream display-name change is also review-gated so the corresponding
NORMAL visual form cannot silently drift out of sync.

`card-overrides.json` is the source of CycleLens semantics: stable canonical IDs,
short names, card type, cycle eligibility, exclusions, and visual-form mappings.
The official response supplies only upstream identity, display name, and artwork
URL. Android never runs this tool and never downloads card data at runtime.
