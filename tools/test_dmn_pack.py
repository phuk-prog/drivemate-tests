"""Checks for the DMN road pack writer. Run: python3 -m unittest tools.test_dmn_pack"""
from pathlib import Path
import tempfile
import unittest

from tools import dmn_pack as d

HERE = Path(__file__).resolve().parent


class DmnPackTests(unittest.TestCase):
    def test_access_rules_match_the_app_packs(self):
        self.assertEqual((d.ALLOWED, False), d.car_access({}, 'residential'))
        self.assertEqual((d.UNKNOWN, False), d.car_access({}, 'service'), 'service roads: start or end only')
        self.assertEqual((d.UNKNOWN, False), d.car_access({}, 'track'))
        self.assertEqual((d.ALLOWED, False), d.car_access({'access': 'yes'}, 'service'))
        self.assertEqual((d.UNKNOWN, True), d.car_access({'access': 'private'}, 'residential'))
        self.assertEqual((d.UNKNOWN, True), d.car_access({'motor_vehicle': 'destination'}, 'unclassified'))
        self.assertEqual((d.PROHIBITED, False), d.car_access({'motor_vehicle': 'no'}, 'residential'))
        self.assertEqual((d.ALLOWED, False), d.car_access({'access': 'no', 'motorcar': 'yes'}, 'residential'),
                         "a car's own rule wins")

    def test_speed_is_the_usual_speed_lowered_to_the_limit(self):
        self.assertEqual(95, d.speed_kph({}, 'motorway'))
        self.assertAlmostEqual(80.467, d.speed_kph({'maxspeed': '50 mph'}, 'motorway'), places=2)
        self.assertEqual(30, d.speed_kph({'maxspeed': '20 mph'}, 'residential'), 'a higher limit never raises it')
        self.assertEqual(55, d.speed_kph({'maxspeed': 'national'}, 'primary'))
        self.assertEqual(40, d.speed_kph({'maxspeed': '40'}, 'secondary'))

    def test_one_way_rules(self):
        self.assertEqual(1, d.direction({'oneway': 'yes'}))
        self.assertEqual(-1, d.direction({'oneway': '-1'}))
        self.assertEqual(1, d.direction({'junction': 'roundabout'}))
        self.assertEqual(1, d.direction({'highway': 'motorway'}))
        self.assertEqual(0, d.direction({'highway': 'motorway', 'oneway': 'no'}))
        self.assertEqual(0, d.direction({}))

    def test_barriers_and_gates(self):
        self.assertTrue(d.barrier_closed({'barrier': 'bollard'}))
        self.assertFalse(d.barrier_closed({'barrier': 'bollard', 'motor_vehicle': 'yes'}))
        self.assertIsNone(d.gate_access({'barrier': 'bollard'}))
        self.assertEqual((d.UNKNOWN, False), d.gate_access({'barrier': 'gate'}))
        self.assertEqual((d.UNKNOWN, True), d.gate_access({'barrier': 'gate', 'access': 'private'}))
        self.assertEqual((d.PROHIBITED, False), d.gate_access({'barrier': 'lift_gate', 'motor_vehicle': 'no'}))
        self.assertIsNone(d.gate_access({'barrier': 'gate', 'access': 'yes'}))

    def test_fixture_is_what_the_tool_writes_today(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'dmn-test.dmnpack'
            self.assertEqual(0, d.main([str(HERE / 'fixtures/dmn-test.osm'), str(out), '--source', 'drivemate-tests fixture']))
            committed = HERE.parent / 'app-unit/resources/dmn/dmn-test.dmnpack'
            self.assertEqual(committed.read_bytes(), out.read_bytes(),
                             'rebuild the fixture: python3 -m tools.dmn_pack tools/fixtures/dmn-test.osm '
                             'app-unit/resources/dmn/dmn-test.dmnpack --source "drivemate-tests fixture"')

    def test_no_car_roads(self):
        with tempfile.TemporaryDirectory() as tmp:
            osm = Path(tmp) / 'paths.osm'
            osm.write_text('<?xml version="1.0"?><osm version="0.6"><node id="1" version="1" lat="53" lon="-2"/>'
                           '<node id="2" version="1" lat="53.001" lon="-2"/><way id="1" version="1"><nd ref="1"/>'
                           '<nd ref="2"/><tag k="highway" v="footway"/></way></osm>')
            self.assertEqual(3, d.main([str(osm), str(Path(tmp) / 'x.dmnpack')]))


if __name__ == '__main__':
    unittest.main()
