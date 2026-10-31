import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BatchSelectionTest(unittest.TestCase):
    def setUp(self):
        p=ROOT/'ci/test_selection.py'
        self.assertTrue(p.is_file(), 'Batch selection is not implemented')
        spec=importlib.util.spec_from_file_location('selection',p)
        self.s=importlib.util.module_from_spec(spec); spec.loader.exec_module(self.s)

    def test_lane_batch_keeps_lane_checks_and_excludes_unrelated_voice(self):
        self.assertTrue(self.s.selected('categories/03-lane-guidance/screen/LaneGuidanceScreenTest.kt','C'))
        self.assertFalse(self.s.selected('categories/02-voice-and-sound/screen/VoiceScreenTest.kt','C'))

    def test_unit_checks_remain_in_every_batch(self):
        self.assertTrue(self.s.selected('app-unit/java/com/drivemate/app/data/UkJoinTest.kt','C'))

    def test_all_selects_every_historical_category(self):
        for n in map(int, self.s.manifest()['test_source_directory_category_map']):
            self.assertTrue(self.s.selected(f'categories/{n:02d}-category/screen/Test.kt','all'))

    def test_unknown_batch_is_an_error(self):
        with self.assertRaisesRegex(ValueError,'UNKNOWN_BATCH'):
            self.s.selected('app-unit/java/Test.kt','mistyped')

    def test_noncategory_kotlin_source_cannot_silently_disappear(self):
        with self.assertRaisesRegex(ValueError,'UNMAPPED_CATEGORY_SOURCE'):
            self.s.selected('categories/new-feature/screen/Test.kt','C')


if __name__=='__main__': unittest.main()
