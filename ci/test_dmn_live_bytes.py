"""Frozen Category 2.3 tests: actual bytes of four small, published UK DMN pieces.

Synthetic data is only a compressed-format unit fixture. It is NEVER a
synthetic UK road, verified routing session, or legal road assertion.
"""
import gzip
import hashlib
import unittest
from dmn_live_bytes import sample_pieces, check_piece_bytes

SQUARES = {"england_manchester": "213_-9",
           "wales_cardiff": "205_-13",
           "northern_ireland_belfast": "218_-24",
           "scotland_edinburgh": "223_-13"}


def entry(name, raw):
    return {"file": name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


class PublishedFourNationDMNBytes(unittest.TestCase):
    def setUp(self):
        self.sample = gzip.compress(b"DMNPACK2" + b"format-only, not road geometry")
        self.item = entry("dmn-213_-9.dmnpack", self.sample)

    def test_exact_four_nation_square_selection(self):
        tiles = {square: entry("dmn-" + square + ".dmnpack", self.sample)
                 for square in SQUARES.values()}
        chosen = sample_pieces({"tiles": tiles, "release": "dmn-uk-20261028-15"})
        self.assertEqual(SQUARES, {name: x["square"] for name, x in chosen.items()})
        self.assertEqual(4, len(chosen))

    def test_missing_nation_square_fails_closed(self):
        tiles = {square: entry("dmn-" + square + ".dmnpack", self.sample)
                 for square in list(SQUARES.values())[:3]}
        with self.assertRaisesRegex(ValueError, "MISSING_NATION_SAMPLE"):
            sample_pieces({"tiles": tiles, "release": "dmn-uk-20261028-15"})

    def test_matching_downloaded_bytes_and_version_are_only_byte_evidence(self):
        result = check_piece_bytes(self.sample, self.item)
        self.assertEqual("published_dmn_piece_bytes_only", result["scope"])
        self.assertEqual(len(self.sample), result["verified_bytes"])
        self.assertFalse(result["android_parser_verified"])
        self.assertFalse(result["offline_navigation_accepted"])
        self.assertEqual("unknown", result["independent_legal_truth"])

    def test_changed_bytes_fails_checksum(self):
        with self.assertRaisesRegex(ValueError, "DMN_PUBLISHED_BYTES_MISMATCH"):
            check_piece_bytes(self.sample + b"x", self.item)

    def test_wrong_gzip_payload_version_fails(self):
        raw = gzip.compress(b"NOTDMNP2")
        with self.assertRaisesRegex(ValueError, "DMN_PACK_MAGIC_INVALID"):
            check_piece_bytes(raw, entry("dmn-213_-9.dmnpack", raw))

    def test_corrupt_gzip_fails_without_parsing_roads(self):
        raw = b"corrupt compressed bytes"
        with self.assertRaisesRegex(ValueError, "DMN_GZIP_INVALID"):
            check_piece_bytes(raw, entry("dmn-213_-9.dmnpack", raw))

    def test_oversized_metadata_cannot_trigger_download(self):
        item = entry("dmn-213_-9.dmnpack", self.sample)
        item["bytes"] = 100_000_000
        with self.assertRaisesRegex(ValueError, "DMN_SAMPLE_TOO_LARGE"):
            check_piece_bytes(self.sample, item)


if __name__ == "__main__":
    unittest.main()
