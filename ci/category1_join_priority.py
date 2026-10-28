"""Category 1: immutable UK-join cache keys must reflect newest-region priority.

The production join merges overlapping regional copies in publication order:
the first version of an OSM way wins. This regression tests only release
metadata ordering, not synthetic roads, lane arrows or UK legal truth.
"""
import os
from pathlib import Path
import sys
import unittest

MAP = Path(os.environ.get("DRIVEMATE_MAP_SRC", "map-src")).resolve()
sys.path.insert(0, str(MAP / "scripts"))
import uk_join


class Category1JoinPriorityTest(unittest.TestCase):
    def setUp(self):
        # Metadata-only fixture: two distinct immutable regional file digests.
        self.newest = {"region": "manchester", "sha256": "a" * 64}
        self.older = {"region": "stockport", "sha256": "b" * 64}

    def test_reversing_overlapping_region_precedence_invalidates_cache(self):
        self.assertNotEqual(
            uk_join.sources_key([self.newest, self.older]),
            uk_join.sources_key([self.older, self.newest]),
            "The first regional copy wins in merge(); priority changes must rebuild",
        )

    def test_same_precedence_reuses_same_cache_key(self):
        self.assertEqual(
            uk_join.sources_key([self.newest, self.older]),
            uk_join.sources_key([dict(self.newest), dict(self.older)]),
        )

    def test_modified_source_bytes_invalidate_cache(self):
        changed = {**self.older, "sha256": "c" * 64}
        self.assertNotEqual(
            uk_join.sources_key([self.newest, self.older]),
            uk_join.sources_key([self.newest, changed]),
        )


if __name__ == "__main__":
    unittest.main()
