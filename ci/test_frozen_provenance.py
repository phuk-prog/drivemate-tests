import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FrozenProvenanceTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'ci/verify_frozen_lanes.py'
        self.assertTrue(path.is_file(), 'Frozen source comparison is missing')
        spec = importlib.util.spec_from_file_location('frozen', path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.fixture = json.loads((ROOT / 'app-unit/resources/cat3-published-manchester-lanes-3.json').read_text())
        self.source = json.loads((ROOT / 'provenance/manchester-lanes/source-samples.json').read_text())

    def test_original_three_ways_match_archived_source(self):
        self.assertEqual(3, self.module.verify(self.fixture, self.source)['correct'])

    def test_geometry_cannot_change_while_claiming_original_digest(self):
        self.fixture['ways'][0]['g'][0][0] += 0.001
        with self.assertRaisesRegex(ValueError, 'source_way_mismatch'):
            self.module.verify(self.fixture, self.source)

    def test_lane_arrows_cannot_change_while_claiming_original_digest(self):
        self.fixture['ways'][0]['t']['turn:lanes'] = 'right|right'
        with self.assertRaisesRegex(ValueError, 'source_way_mismatch'):
            self.module.verify(self.fixture, self.source)

    def test_duplicate_ids_are_rejected(self):
        self.fixture['ways'].append(copy.deepcopy(self.fixture['ways'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate_way'):
            self.module.verify(self.fixture, self.source)

    def test_source_digest_mismatch_is_rejected(self):
        self.fixture['source_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'source_digest_mismatch'):
            self.module.verify(self.fixture, self.source)

    def test_snapshot_bytes_are_verified(self):
        self.assertEqual(12, len(self.module.load_source(ROOT / 'provenance/manchester-lanes')['ways']))


if __name__ == '__main__':
    unittest.main()
