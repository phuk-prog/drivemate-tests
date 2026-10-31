from pathlib import Path
import tempfile
import unittest


class DependencyGraphTest(unittest.TestCase):
    def test_graph_is_backed_by_imported_production_source(self):
        from dependency_graph import build
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); app=root/'app'; tests=root/'tests'
            source=app/'app/src/main/java/com/drivemate/app/data/LaneDb.kt'
            source.parent.mkdir(parents=True); source.write_text('package com.drivemate.app.data\nclass LaneDb\n')
            test=tests/'categories/03-lane-guidance/decisions/LaneTest.kt'
            test.parent.mkdir(parents=True); test.write_text('import com.drivemate.app.data.LaneDb\n')
            r=build(app,tests)
            self.assertEqual(24,len(r['nodes']))
            self.assertEqual((9,2),(r['edges'][0]['category'],r['edges'][0]['prerequisite']))
            self.assertEqual(64,len(r['edges'][0]['source_sha256']))
            self.assertEqual('not_observed',r['nodes'][1]['status'])


if __name__=='__main__': unittest.main()
