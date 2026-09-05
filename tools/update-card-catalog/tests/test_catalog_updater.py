import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from catalog_updater import (  # noqa: E402
    PNG_SIGNATURE,
    UpdaterError,
    apply_update,
    load_overrides,
    parse_official_cards_response,
    synchronize,
)


class CatalogUpdaterTest(unittest.TestCase):
    def test_valid_official_response_is_parsed(self):
        cards = parse_official_cards_response(self.response())

        self.assertEqual(1, len(cards))
        self.assertEqual(26000000, cards[0].supercell_id)
        self.assertEqual("Knight", cards[0].name)
        self.assertEqual("https://example.test/knight.png", cards[0].icon_url)
        self.assertEqual("CARD", cards[0].source_group)
        self.assertEqual(3, cards[0].elixir_cost)

    def test_missing_icon_url_is_explicit(self):
        cards = parse_official_cards_response(self.response(icon_url=None))

        self.assertIsNone(cards[0].icon_url)

    def test_support_items_are_parsed_as_separate_official_objects(self):
        payload = json.dumps(
            {
                "items": [self.item()],
                "supportItems": [
                    {
                        "id": 159000000,
                        "name": "Tower Princess",
                        "iconUrls": {"medium": "https://example.test/tower.png"},
                    }
                ],
            }
        )

        cards = parse_official_cards_response(payload)

        self.assertEqual(2, len(cards))
        self.assertEqual("SUPPORT", cards[1].source_group)
        self.assertIsNone(cards[1].elixir_cost)

    def test_duplicate_supercell_id_is_rejected(self):
        item = self.item()
        with self.assertRaises(UpdaterError):
            parse_official_cards_response(json.dumps({"items": [item, item]}))

    def test_malformed_response_is_rejected(self):
        for payload in ("{", "[]", '{"items":{}}', '{"items":[],"supportItems":{}}'):
            with self.subTest(payload=payload):
                with self.assertRaises(UpdaterError):
                    parse_official_cards_response(payload)

    def test_unknown_card_becomes_review_candidate(self):
        with tempfile.TemporaryDirectory() as temporary:
            overrides, forms = self.overrides(Path(temporary))
            official = parse_official_cards_response(
                json.dumps({"items": [self.item(card_id=26000999, name="New Card")]})
            )

            result = synchronize(official, overrides, forms, Path(temporary) / "icons")

        self.assertEqual(1, len(result.report.new_candidates))
        self.assertEqual("new_card", result.report.new_candidates[0]["candidateId"])
        self.assertEqual(0, len(result.catalog["cards"]))

    def test_existing_canonical_id_is_preserved_when_display_name_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            overrides, forms = self.overrides(Path(temporary))
            official = parse_official_cards_response(self.response(name="Knight Renamed"))

            result = synchronize(official, overrides, forms, Path(temporary) / "icons")

        self.assertEqual("knight", result.catalog["cards"][0]["id"])
        self.assertEqual("Knight", result.catalog["cards"][0]["displayName"])
        self.assertEqual(1, len(result.report.upstream_name_changes))
        self.assertEqual(forms, tuple(result.catalog["visualForms"]))

    def test_update_refuses_upstream_name_change_until_reviewed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(self.response(name="Knight Renamed"))
            result = synchronize(official, overrides, forms, root / "icons")

            with self.assertRaises(UpdaterError):
                apply_update(result, root / "cards.json", root / "icons")

    def test_advertised_visual_form_requires_reviewed_mapping(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(
                json.dumps({"items": [self.item(has_evolution=True)]})
            )

            result = synchronize(official, overrides, forms, root / "icons")

        self.assertEqual(
            ({"status": "UNMAPPED VISUAL/CARD CANDIDATE", "canonicalId": "knight", "form": "EVOLUTION"},),
            result.report.unmapped_visual_candidates,
        )

    def test_official_elixir_change_requires_semantic_review(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(self.response(elixir_cost=4))

            result = synchronize(official, overrides, forms, root / "icons")

        self.assertEqual(1, len(result.report.semantic_mismatches))

    def test_dry_run_model_does_not_write_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(self.response())

            synchronize(official, overrides, forms, root / "icons")

            self.assertFalse((root / "cards.json").exists())
            self.assertFalse((root / "icons").exists())

    def test_update_refuses_unreviewed_card(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(
                json.dumps({"items": [self.item(card_id=26000999, name="Unknown")]})
            )
            result = synchronize(official, overrides, forms, root / "icons")

            with self.assertRaises(UpdaterError):
                apply_update(result, root / "cards.json", root / "icons")

    def test_update_refuses_included_card_without_artwork_url(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(self.response(icon_url=None))
            result = synchronize(official, overrides, forms, root / "icons")

            with self.assertRaises(UpdaterError):
                apply_update(result, root / "cards.json", root / "icons")

    def test_update_preserves_original_png_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            overrides, forms = self.overrides(root)
            official = parse_official_cards_response(self.response())
            result = synchronize(official, overrides, forms, root / "icons")
            png = PNG_SIGNATURE + b"original bytes"

            apply_update(
                result,
                root / "cards.json",
                root / "icons",
                downloader=lambda _: png,
            )

            self.assertEqual(png, (root / "icons/26000000.png").read_bytes())
            catalog = json.loads((root / "cards.json").read_text(encoding="utf-8"))
            self.assertEqual("knight", catalog["cards"][0]["id"])
            self.assertEqual(1, len(catalog["visualForms"]))

    def response(
        self,
        *,
        name="Knight",
        icon_url="https://example.test/knight.png",
        elixir_cost=3,
    ):
        return json.dumps(
            {"items": [self.item(name=name, icon_url=icon_url, elixir_cost=elixir_cost)]}
        )

    @staticmethod
    def item(
        *,
        card_id=26000000,
        name="Knight",
        icon_url="https://example.test/knight.png",
        elixir_cost=3,
        has_evolution=False,
    ):
        item = {"id": card_id, "name": name, "elixirCost": elixir_cost}
        if icon_url is not None:
            item["iconUrls"] = {"medium": icon_url}
            if has_evolution:
                item["iconUrls"]["evolutionMedium"] = "https://example.test/evolution.png"
        return item

    def overrides(self, root):
        path = root / "card-overrides.json"
        path.write_text(
            json.dumps(
                {
                    "cards": [
                        {
                            "id": "knight",
                            "supercellId": 26000000,
                            "upstreamName": "Knight",
                            "shortName": "Knight",
                            "type": "TROOP",
                            "elixir": 3,
                            "cycleEligible": True,
                            "include": True,
                        }
                    ],
                    "visualForms": [
                        {
                            "id": "knight_normal",
                            "canonicalCardId": "knight",
                            "form": "NORMAL",
                            "displayName": "Knight",
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        return load_overrides(path)


if __name__ == "__main__":
    unittest.main()
