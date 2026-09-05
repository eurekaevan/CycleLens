#!/usr/bin/env python3
"""Development-only Clash Royale catalog updater.

The Android application never imports or executes this module. It consumes only
the reviewed cards.json and PNG assets produced by an explicit update run.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import unicodedata
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


API_URL = "https://api.clashroyale.com/v1/cards"
TOKEN_ENVIRONMENT_VARIABLE = "CLASH_ROYALE_API_TOKEN"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
VALID_CARD_TYPES = {"TROOP", "SPELL", "BUILDING", "TOWER_TROOP"}
VALID_CARD_FORMS = {"NORMAL", "EVOLUTION", "HERO"}
SNAKE_CASE = re.compile(r"[a-z0-9]+(?:_[a-z0-9]+)*\Z")


class UpdaterError(ValueError):
    pass


@dataclass(frozen=True)
class OfficialCard:
    supercell_id: int
    name: str
    icon_url: str | None
    source_group: str
    elixir_cost: int | None
    has_evolution: bool
    has_hero: bool


@dataclass(frozen=True)
class CardOverride:
    canonical_id: str
    supercell_id: int | None
    upstream_name: str
    short_name: str
    card_type: str | None
    elixir: int | None
    cycle_eligible: bool
    include: bool


@dataclass(frozen=True)
class SyncReport:
    official_cards: int
    matched_existing: int
    new_candidates: tuple[dict[str, Any], ...]
    missing_locally: int
    missing_artwork: int
    stale_local_cards: tuple[str, ...]
    upstream_name_changes: tuple[dict[str, Any], ...]
    unmapped_visual_candidates: tuple[dict[str, Any], ...]
    semantic_mismatches: tuple[dict[str, Any], ...]

    def lines(self) -> tuple[str, ...]:
        return (
            f"Official API cards: {self.official_cards}",
            f"Matched existing canonical cards: {self.matched_existing}",
            f"New candidates: {len(self.new_candidates)}",
            f"Missing locally: {self.missing_locally}",
            f"Missing artwork: {self.missing_artwork}",
            f"Stale local cards: {len(self.stale_local_cards)}",
            f"Upstream name changes requiring review: {len(self.upstream_name_changes)}",
            f"Unmapped visual candidates: {len(self.unmapped_visual_candidates)}",
            f"Semantic mismatches requiring review: {len(self.semantic_mismatches)}",
        )


@dataclass(frozen=True)
class SyncResult:
    catalog: dict[str, Any]
    downloads: tuple[tuple[OfficialCard, str], ...]
    report: SyncReport


def parse_official_cards_response(payload: str) -> tuple[OfficialCard, ...]:
    try:
        root = json.loads(payload)
    except json.JSONDecodeError as error:
        raise UpdaterError("Official cards response is malformed JSON") from error
    if not isinstance(root, dict) or not isinstance(root.get("items"), list):
        raise UpdaterError("Official cards response requires an items array")

    cards: list[OfficialCard] = []
    seen_ids: set[int] = set()
    groups = (("CARD", root["items"]), ("SUPPORT", root.get("supportItems", [])))
    if not isinstance(groups[1][1], list):
        raise UpdaterError("Official cards response supportItems must be an array")
    for source_group, items in groups:
        for index, item in enumerate(items):
            cards.append(_parse_official_card(item, index, source_group, seen_ids))
    return tuple(cards)


def _parse_official_card(
    item: Any,
    index: int,
    source_group: str,
    seen_ids: set[int],
) -> OfficialCard:
    if not isinstance(item, dict):
        raise UpdaterError(
            f"Official {source_group.lower()} item at index {index} must be an object"
        )
    supercell_id = item.get("id")
    name = item.get("name")
    if isinstance(supercell_id, bool) or not isinstance(supercell_id, int):
        raise UpdaterError(f"Official item at index {index} requires an integer id")
    if supercell_id <= 0:
        raise UpdaterError(f"Official item at index {index} requires a positive id")
    if supercell_id in seen_ids:
        raise UpdaterError(f"Duplicate official card id: {supercell_id}")
    if not isinstance(name, str) or not name.strip():
        raise UpdaterError(f"Official item at index {index} requires a non-blank name")

    icon_urls = item.get("iconUrls")
    if icon_urls is not None and not isinstance(icon_urls, dict):
        raise UpdaterError(f"Official item at index {index} has invalid iconUrls")
    icon_url = icon_urls.get("medium") if icon_urls else None
    if icon_url is not None and (not isinstance(icon_url, str) or not icon_url.strip()):
        raise UpdaterError(f"Official item at index {index} has invalid medium icon URL")
    elixir_cost = item.get("elixirCost")
    if elixir_cost is not None and (
        isinstance(elixir_cost, bool)
        or not isinstance(elixir_cost, int)
        or not 0 <= elixir_cost <= 10
    ):
        raise UpdaterError(f"Official item at index {index} has invalid elixirCost")

    seen_ids.add(supercell_id)
    return OfficialCard(
        supercell_id=supercell_id,
        name=name.strip(),
        icon_url=icon_url,
        source_group=source_group,
        elixir_cost=elixir_cost,
        has_evolution=bool(icon_urls and icon_urls.get("evolutionMedium")),
        has_hero=bool(icon_urls and icon_urls.get("heroMedium")),
    )


def load_overrides(path: Path) -> tuple[tuple[CardOverride, ...], tuple[dict[str, Any], ...]]:
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise UpdaterError(f"Unable to read overrides: {path}") from error
    if not isinstance(root, dict):
        raise UpdaterError("Overrides root must be an object")
    raw_cards = root.get("cards")
    raw_forms = root.get("visualForms")
    if not isinstance(raw_cards, list) or not isinstance(raw_forms, list):
        raise UpdaterError("Overrides require cards and visualForms arrays")

    overrides = tuple(_parse_override(item, index) for index, item in enumerate(raw_cards))
    canonical_ids = [item.canonical_id for item in overrides]
    if len(canonical_ids) != len(set(canonical_ids)):
        raise UpdaterError("Override canonical IDs must be unique")
    supercell_ids = [item.supercell_id for item in overrides if item.supercell_id is not None]
    if len(supercell_ids) != len(set(supercell_ids)):
        raise UpdaterError("Override Supercell IDs must be unique")
    upstream_names = [item.upstream_name for item in overrides]
    if len(upstream_names) != len(set(upstream_names)):
        raise UpdaterError("Override upstream names must be unique")

    forms = tuple(_parse_visual_form(item, index) for index, item in enumerate(raw_forms))
    form_ids = [item["id"] for item in forms]
    if len(form_ids) != len(set(form_ids)):
        raise UpdaterError("Override visual form IDs must be unique")
    included_ids = {item.canonical_id for item in overrides if item.include}
    for form in forms:
        if form["canonicalCardId"] not in included_ids:
            raise UpdaterError(
                f"Visual form {form['id']} references an excluded or unknown canonical card"
            )
    included_by_id = {
        item.canonical_id: item for item in overrides if item.include
    }
    for canonical_id, override in included_by_id.items():
        normal_forms = [
            form
            for form in forms
            if form["canonicalCardId"] == canonical_id and form["form"] == "NORMAL"
        ]
        if len(normal_forms) != 1:
            raise UpdaterError(
                f"Included card {canonical_id} must have exactly one NORMAL visual form"
            )
        if normal_forms[0]["displayName"] != override.upstream_name:
            raise UpdaterError(
                f"NORMAL visual form name must match upstreamName for {canonical_id}"
            )
    return overrides, forms


def _parse_override(item: Any, index: int) -> CardOverride:
    if not isinstance(item, dict):
        raise UpdaterError(f"Card override at index {index} must be an object")
    canonical_id = _required_string(item, "id", "Card override", index)
    upstream_name = _required_string(item, "upstreamName", "Card override", index)
    short_name = _required_string(item, "shortName", "Card override", index)
    if not SNAKE_CASE.fullmatch(canonical_id):
        raise UpdaterError(f"Invalid canonical ID in override: {canonical_id}")
    supercell_id = item.get("supercellId")
    if supercell_id is not None and (
        isinstance(supercell_id, bool) or not isinstance(supercell_id, int) or supercell_id <= 0
    ):
        raise UpdaterError(f"Card override {canonical_id} has invalid supercellId")
    card_type = item.get("type")
    if card_type is not None and card_type not in VALID_CARD_TYPES:
        raise UpdaterError(f"Card override {canonical_id} has unknown type")
    elixir = item.get("elixir")
    if elixir is not None and (
        isinstance(elixir, bool) or not isinstance(elixir, int) or not 1 <= elixir <= 10
    ):
        raise UpdaterError(f"Card override {canonical_id} has invalid elixir")
    cycle_eligible = item.get("cycleEligible")
    include = item.get("include", True)
    if not isinstance(cycle_eligible, bool) or not isinstance(include, bool):
        raise UpdaterError(
            f"Card override {canonical_id} requires boolean cycleEligible and include"
        )
    if card_type == "TOWER_TROOP" and cycle_eligible:
        raise UpdaterError(f"Tower Troop {canonical_id} cannot be cycle eligible")
    return CardOverride(
        canonical_id=canonical_id,
        supercell_id=supercell_id,
        upstream_name=upstream_name,
        short_name=short_name,
        card_type=card_type,
        elixir=elixir,
        cycle_eligible=cycle_eligible,
        include=include,
    )


def _parse_visual_form(item: Any, index: int) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise UpdaterError(f"Visual form override at index {index} must be an object")
    form_id = _required_string(item, "id", "Visual form override", index)
    canonical_id = _required_string(item, "canonicalCardId", "Visual form override", index)
    display_name = _required_string(item, "displayName", "Visual form override", index)
    form = _required_string(item, "form", "Visual form override", index)
    if not SNAKE_CASE.fullmatch(form_id) or not SNAKE_CASE.fullmatch(canonical_id):
        raise UpdaterError(f"Invalid visual form ID or canonical reference: {form_id}")
    if form not in VALID_CARD_FORMS:
        raise UpdaterError(f"Unknown visual form: {form}")
    return {
        "id": form_id,
        "canonicalCardId": canonical_id,
        "form": form,
        "displayName": display_name,
    }


def _required_string(item: dict[str, Any], field: str, label: str, index: int) -> str:
    value = item.get(field)
    if not isinstance(value, str) or not value.strip():
        raise UpdaterError(f"{label} at index {index} requires non-blank {field}")
    return value.strip()


def candidate_canonical_id(name: str) -> str:
    normalized = unicodedata.normalize("NFKD", name)
    ascii_name = normalized.encode("ascii", "ignore").decode("ascii").lower()
    candidate = re.sub(r"[^a-z0-9]+", "_", ascii_name).strip("_")
    return candidate or "review_required"


def synchronize(
    official_cards: tuple[OfficialCard, ...],
    overrides: tuple[CardOverride, ...],
    visual_forms: tuple[dict[str, Any], ...],
    icon_directory: Path,
) -> SyncResult:
    by_id = {item.supercell_id: item for item in overrides if item.supercell_id is not None}
    by_name = {item.upstream_name: item for item in overrides}
    matched_override_ids: set[str] = set()
    cards: list[dict[str, Any]] = []
    downloads: list[tuple[OfficialCard, str]] = []
    new_candidates: list[dict[str, Any]] = []
    upstream_name_changes: list[dict[str, Any]] = []
    unmapped_visual_candidates: list[dict[str, Any]] = []
    semantic_mismatches: list[dict[str, Any]] = []
    missing_artwork = 0

    for official in official_cards:
        override = by_id.get(official.supercell_id) or by_name.get(official.name)
        if override is None:
            new_candidates.append(
                {
                    "status": "NEW CARD REQUIRES REVIEW",
                    "supercellId": official.supercell_id,
                    "upstreamName": official.name,
                    "candidateId": candidate_canonical_id(official.name),
                    "hasIconUrl": official.icon_url is not None,
                    "sourceGroup": official.source_group,
                    "elixirCost": official.elixir_cost,
                    "hasEvolution": official.has_evolution,
                    "hasHero": official.has_hero,
                }
            )
            continue
        matched_override_ids.add(override.canonical_id)
        if not override.include:
            continue
        if official.name != override.upstream_name:
            upstream_name_changes.append(
                {
                    "status": "UPSTREAM NAME CHANGED REQUIRES REVIEW",
                    "canonicalId": override.canonical_id,
                    "reviewedName": override.upstream_name,
                    "officialName": official.name,
                }
            )
        expected_cycle_eligible = official.source_group == "CARD"
        expected_tower_type = official.source_group == "SUPPORT"
        if (
            override.cycle_eligible != expected_cycle_eligible
            or (override.card_type == "TOWER_TROOP") != expected_tower_type
            or (
                official.elixir_cost is not None
                and override.elixir != official.elixir_cost
            )
        ):
            semantic_mismatches.append(
                {
                    "canonicalId": override.canonical_id,
                    "sourceGroup": official.source_group,
                    "officialElixir": official.elixir_cost,
                    "reviewedElixir": override.elixir,
                    "reviewedType": override.card_type,
                    "reviewedCycleEligible": override.cycle_eligible,
                }
            )
        mapped_forms = [
            form for form in visual_forms if form["canonicalCardId"] == override.canonical_id
        ]
        mapped_form_types = {form["form"] for form in mapped_forms}
        expected_forms = {"NORMAL"}
        if official.has_evolution:
            expected_forms.add("EVOLUTION")
        if official.has_hero:
            expected_forms.add("HERO")
        for missing_form in sorted(expected_forms - mapped_form_types):
            unmapped_visual_candidates.append(
                {
                    "status": "UNMAPPED VISUAL/CARD CANDIDATE",
                    "canonicalId": override.canonical_id,
                    "form": missing_form,
                }
            )

        icon_asset_path = None
        if official.icon_url is not None:
            icon_asset_path = f"card-icons/{official.supercell_id}.png"
            downloads.append((official, icon_asset_path))
            if not (icon_directory / f"{official.supercell_id}.png").is_file():
                missing_artwork += 1
        else:
            missing_artwork += 1
        cards.append(
            {
                "id": override.canonical_id,
                "supercellId": official.supercell_id,
                "displayName": override.upstream_name,
                "shortName": override.short_name,
                "type": override.card_type,
                "elixir": override.elixir,
                "cycleEligible": override.cycle_eligible,
                "iconAssetPath": icon_asset_path,
            }
        )

    stale = tuple(
        item.canonical_id
        for item in overrides
        if item.include and item.canonical_id not in matched_override_ids
    )
    report = SyncReport(
        official_cards=len(official_cards),
        matched_existing=len(matched_override_ids),
        new_candidates=tuple(new_candidates),
        missing_locally=len(new_candidates),
        missing_artwork=missing_artwork,
        stale_local_cards=stale,
        upstream_name_changes=tuple(upstream_name_changes),
        unmapped_visual_candidates=tuple(unmapped_visual_candidates),
        semantic_mismatches=tuple(semantic_mismatches),
    )
    return SyncResult(
        catalog={"cards": cards, "visualForms": list(visual_forms)},
        downloads=tuple(downloads),
        report=report,
    )


def fetch_official_cards(token: str) -> str:
    request = urllib.request.Request(
        API_URL,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8")
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
        raise UpdaterError(f"Official cards request failed: {error}") from error


def download_and_validate_png(url: str) -> bytes:
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            content = response.read()
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
        raise UpdaterError(f"Artwork download failed: {url}") from error
    if not content.startswith(PNG_SIGNATURE):
        raise UpdaterError(f"Artwork is not a PNG: {url}")
    return content


def apply_update(
    result: SyncResult,
    catalog_path: Path,
    icon_directory: Path,
    downloader: Callable[[str], bytes] = download_and_validate_png,
) -> None:
    if result.report.new_candidates:
        raise UpdaterError("Update refused: new cards require review")
    if result.report.stale_local_cards:
        raise UpdaterError("Update refused: stale local cards require review")
    if result.report.upstream_name_changes:
        raise UpdaterError("Update refused: upstream name changes require review")
    if result.report.unmapped_visual_candidates:
        raise UpdaterError("Update refused: visual form candidates require review")
    if result.report.semantic_mismatches:
        raise UpdaterError("Update refused: semantic mismatches require review")
    missing_urls = [
        card["displayName"]
        for card in result.catalog["cards"]
        if card["iconAssetPath"] is None
    ]
    if missing_urls:
        raise UpdaterError("Update refused: included cards are missing artwork URLs")

    icon_directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(
        prefix=".cyclelens-card-icons-",
        dir=icon_directory.parent,
    ) as temporary:
        staging = Path(temporary)
        staged: list[tuple[Path, Path]] = []
        for official, asset_path in result.downloads:
            if official.icon_url is None:
                continue
            content = downloader(official.icon_url)
            if not content.startswith(PNG_SIGNATURE):
                raise UpdaterError(f"Artwork is not a PNG: {official.name}")
            staged_path = staging / f"{official.supercell_id}.png"
            staged_path.write_bytes(content)
            staged.append((staged_path, icon_directory / staged_path.name))
        for source, destination in staged:
            os.replace(source, destination)

    catalog_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_catalog = catalog_path.with_suffix(".json.tmp")
    temporary_catalog.write_text(
        json.dumps(result.catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary_catalog, catalog_path)


def _arguments() -> argparse.Namespace:
    script_directory = Path(__file__).resolve().parent
    repository_root = script_directory.parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true", help="write reviewed JSON and PNGs")
    parser.add_argument("--response-file", type=Path, help="offline official API response fixture")
    parser.add_argument(
        "--overrides",
        type=Path,
        default=script_directory / "card-overrides.json",
    )
    parser.add_argument(
        "--catalog",
        type=Path,
        default=repository_root / "app/src/main/assets/cards.json",
    )
    parser.add_argument(
        "--icons",
        type=Path,
        default=repository_root / "app/src/main/assets/card-icons",
    )
    return parser.parse_args()


def main() -> int:
    arguments = _arguments()
    try:
        if arguments.response_file:
            payload = arguments.response_file.read_text(encoding="utf-8")
        else:
            token = os.environ.get(TOKEN_ENVIRONMENT_VARIABLE)
            if not token:
                raise UpdaterError(
                    f"{TOKEN_ENVIRONMENT_VARIABLE} is required when no --response-file is supplied"
                )
            payload = fetch_official_cards(token)
        official_cards = parse_official_cards_response(payload)
        overrides, visual_forms = load_overrides(arguments.overrides)
        result = synchronize(official_cards, overrides, visual_forms, arguments.icons)
        for line in result.report.lines():
            print(line)
        for candidate in result.report.new_candidates:
            print(f"UNMAPPED VISUAL/CARD CANDIDATE: {json.dumps(candidate, ensure_ascii=False)}")
        for canonical_id in result.report.stale_local_cards:
            print(f"STALE LOCAL CARD REQUIRES REVIEW: {canonical_id}")
        for change in result.report.upstream_name_changes:
            print(f"UPSTREAM NAME CHANGE: {json.dumps(change, ensure_ascii=False)}")
        for candidate in result.report.unmapped_visual_candidates:
            print(f"UNMAPPED VISUAL/CARD CANDIDATE: {json.dumps(candidate, ensure_ascii=False)}")
        for mismatch in result.report.semantic_mismatches:
            print(f"SEMANTIC MISMATCH REQUIRES REVIEW: {json.dumps(mismatch, ensure_ascii=False)}")
        if arguments.update:
            apply_update(result, arguments.catalog, arguments.icons)
            print(f"Updated catalog: {arguments.catalog}")
        else:
            print("Dry-run only; production assets were not modified.")
        return 0
    except (OSError, UpdaterError) as error:
        print(f"Catalog update failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
