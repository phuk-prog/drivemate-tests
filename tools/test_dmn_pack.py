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

    def test_closed_bollard_overrides_turning_circle_in_emitted_road_graph(self):
        # A real DMNPACK graph must not let cars continue through a bollard,
        # even if OSM also labels its node as a turning circle.
        xml = """<?xml version="1.0"?>
<osm version="0.6" generator="drivemate-test">
  <node id="1" version="1" lat="53.400" lon="-2.200"/>
  <node id="2" version="1" lat="53.400" lon="-2.199">
    <tag k="highway" v="turning_circle"/><tag k="barrier" v="bollard"/>
  </node>
  <node id="3" version="1" lat="53.400" lon="-2.198"/>
  <way id="101" version="1"><nd ref="1"/><nd ref="2"/><nd ref="3"/>
    <tag k="highway" v="residential"/></way>
</osm>
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "closed-barrier.osm"
            source.write_text(xml)
            graph = d.build(str(source))
        nodes = {n[2]: i for i, n in enumerate(graph["nodes"])}
        edges = graph["edges"]
        mid = nodes[2]
        approach = next(i for i, e in enumerate(edges) if e[0] == nodes[1] and e[1] == mid)
        continuation = next(i for i, e in enumerate(edges) if e[0] == mid and e[1] == nodes[3])
        self.assertIn((approach, continuation), graph["bans"])
        self.assertIn(mid, graph["uturn"], "turning back before a bollard remains possible")

    def test_motorway_links_and_reverse_oneway_join_only_by_shared_osm_node(self):
        # Verify graph serialization input, not just the direction() helper:
        # motorway and its slip road connect at node 2; reverse one-way is 5 -> 4.
        xml = """<?xml version="1.0"?>
<osm version="0.6" generator="drivemate-test">
  <node id="1" version="1" lat="53.400" lon="-2.200"/>
  <node id="2" version="1" lat="53.400" lon="-2.199"/>
  <node id="3" version="1" lat="53.401" lon="-2.198"/>
  <node id="4" version="1" lat="53.402" lon="-2.198"/>
  <node id="5" version="1" lat="53.403" lon="-2.198"/>
  <way id="101" version="1"><nd ref="1"/><nd ref="2"/><tag k="highway" v="motorway"/></way>
  <way id="102" version="1"><nd ref="2"/><nd ref="3"/><tag k="highway" v="motorway_link"/></way>
  <way id="103" version="1"><nd ref="3"/><nd ref="4"/><tag k="highway" v="primary"/></way>
  <way id="104" version="1"><nd ref="4"/><nd ref="5"/>
    <tag k="highway" v="primary"/><tag k="oneway" v="-1"/></way>
</osm>
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "motorway.osm"
            source.write_text(xml)
            graph = d.build(str(source))
        nodes = {n[2]: i for i, n in enumerate(graph["nodes"])}
        ways = {w[0]: i for i, w in enumerate(graph["ways"])}
        segments = {(graph["ways"][e[6]][0], graph["nodes"][e[0]][2],
                     graph["nodes"][e[1]][2]) for e in graph["edges"]}
        self.assertIn((101, 1, 2), segments)
        self.assertIn((102, 2, 3), segments)
        self.assertIn((103, 3, 4), segments)
        self.assertIn((103, 4, 3), segments)
        self.assertIn((104, 5, 4), segments)
        self.assertNotIn((101, 2, 1), segments)
        self.assertNotIn((102, 3, 2), segments)
        self.assertNotIn((104, 4, 5), segments)
        self.assertEqual({1, 2, 3, 4, 5}, set(nodes))
        self.assertEqual({101, 102, 103, 104}, set(ways))

    def test_grade_separated_crossing_with_distinct_ids_does_not_connect(self):
        # Coordinate overlap is not a node connection: bridges and underpasses
        # remain disconnected unless they actually share a referenced OSM node.
        xml = """<?xml version="1.0"?>
<osm version="0.6" generator="drivemate-test">
  <node id="1" version="1" lat="53.400" lon="-2.200"/>
  <node id="2" version="1" lat="53.401" lon="-2.199"/>
  <node id="3" version="1" lat="53.401" lon="-2.199"/>
  <node id="4" version="1" lat="53.402" lon="-2.198"/>
  <way id="201" version="1"><nd ref="1"/><nd ref="2"/>
    <tag k="highway" v="primary"/><tag k="bridge" v="yes"/><tag k="layer" v="1"/></way>
  <way id="202" version="1"><nd ref="3"/><nd ref="4"/>
    <tag k="highway" v="secondary"/><tag k="tunnel" v="yes"/><tag k="layer" v="-1"/></way>
</osm>
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "crossing.osm"
            source.write_text(xml)
            graph = d.build(str(source))
        self.assertEqual({1, 2, 3, 4}, {n[2] for n in graph["nodes"]})
        self.assertEqual(4, len(graph["edges"]))
        self.assertFalse(any(graph["ways"][a[6]][0] != graph["ways"][b[6]][0]
                             and a[1] == b[0] for a in graph["edges"] for b in graph["edges"]))

    def test_missing_way_node_does_not_create_teleporting_road_segment(self):
        class Location:
            def __init__(self, ok, lat=53.400, lon=-2.200):
                self.ok, self.lat, self.lon = ok, lat, lon

            def valid(self):
                return self.ok

        class Ref:
            def __init__(self, ident, ok):
                self.ref, self.location = ident, Location(ok)

        class Way:
            id = 301
            tags = {"highway": "residential"}
            nodes = [Ref(1, True), Ref(2, False), Ref(3, True)]

        reader = d.Reader(False)
        reader.way(Way())
        self.assertEqual([], reader.ways, "no link between nodes 1 and 3 may be invented")

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
