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

    def test_directional_and_conditional_car_access_is_not_promoted_to_open(self):
        self.assertEqual((d.PROHIBITED, False),
                         d.car_access({'vehicle:forward': 'no'}, 'residential', 'forward'))
        self.assertEqual((d.ALLOWED, False),
                         d.car_access({'vehicle:forward': 'no'}, 'residential', 'backward'))
        self.assertEqual((d.ALLOWED, False),
                         d.car_access({'motorcar': 'yes', 'vehicle:forward': 'no'}, 'residential', 'forward'),
                         'more specific motorcar allowance takes priority')
        self.assertEqual((d.UNKNOWN, True),
                         d.car_access({'motorcar:conditional': 'no @ (Mo-Fr 08:00-09:00)'}, 'residential', 'forward'))
        self.assertEqual((d.UNKNOWN, True),
                         d.car_access({'vehicle:backward:conditional': 'no @ (Mo-Fr)'}, 'residential', 'backward'))
        self.assertEqual((d.ALLOWED, False),
                         d.car_access({'vehicle:backward:conditional': 'no @ (Mo-Fr)'}, 'residential', 'forward'))
        self.assertEqual((d.UNKNOWN, True),
                         d.car_access({'oneway:conditional': 'yes @ (Mo-Fr)'}, 'residential', 'forward'))
        self.assertEqual((d.UNKNOWN, True),
                         d.car_access({'motorcar': 'yes',
                                       'oneway:conditional': 'yes @ (Mo-Fr)'}, 'residential', 'forward'))
        self.assertEqual((d.PROHIBITED, False),
                         d.car_access({'access': 'no', 'oneway:conditional': 'yes @ (Mo-Fr)'}, 'residential', 'forward'))
        self.assertEqual((d.UNKNOWN, True),
                         d.gate_access({'barrier': 'gate', 'access': 'yes',
                                        'access:conditional': 'no @ (Su)'}))

    def test_directional_and_conditional_access_is_saved_on_each_directed_edge(self):
        xml = """<osm version="0.6">
          <node id="1" version="1" lat="53.400" lon="-2.200"/>
          <node id="2" version="1" lat="53.400" lon="-2.199"/>
          <node id="3" version="1" lat="53.400" lon="-2.198"/>
          <node id="4" version="1" lat="53.400" lon="-2.197"/>
          <way id="100" version="1"><nd ref="1"/><nd ref="2"/>
            <tag k="highway" v="residential"/>
            <tag k="motor_vehicle:forward" v="no"/></way>
          <way id="200" version="1"><nd ref="2"/><nd ref="3"/>
            <tag k="highway" v="residential"/>
            <tag k="motorcar:conditional" v="no @ (Mo-Fr 08:00-09:00)"/></way>
          <way id="300" version="1"><nd ref="3"/><nd ref="4"/>
            <tag k="highway" v="residential"/><tag k="oneway:conditional" v="yes @ (Sa)"/></way>
        </osm>"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "directional.osm"
            source.write_text(xml)
            graph = d.build(str(source))
        arcs = {(graph["ways"][e[6]][0],
                 graph["nodes"][e[0]][2], graph["nodes"][e[1]][2]): (e[4], e[5])
                for e in graph["edges"]}
        self.assertEqual((d.PROHIBITED, False), arcs[100, 1, 2])
        self.assertEqual((d.ALLOWED, False), arcs[100, 2, 1])
        self.assertEqual((d.UNKNOWN, True), arcs[200, 2, 3])
        self.assertEqual((d.UNKNOWN, True), arcs[200, 3, 2])
        self.assertEqual((d.UNKNOWN, True), arcs[300, 3, 4])
        self.assertEqual((d.UNKNOWN, True), arcs[300, 4, 3])

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

    def test_via_way_only_and_no_restrictions_preserve_approach_context(self):
        # A parser/writer fixture, not an assertion of actual UK traffic law.
        def make_graph(kind, conditional=False):
            nodes = "".join(
                f'<node id="{i}" version="1" lat="53.4" lon="{-2.2 + i * 0.001:.3f}"/>'
                for i in range(1, 10))
            ways = [(100, [1, 2]), (200, [2, 3, 4]), (300, [4, 5]),
                    (400, [2, 6]), (500, [3, 7]), (600, [4, 8]), (700, [9, 3])]
            def way(wid, points):
                refs = "".join(f'<nd ref="{n}"/>' for n in points)
                oneway = '<tag k="oneway" v="yes"/>' if wid == 200 else ''
                return f'<way id="{wid}" version="1">{refs}<tag k="highway" v="residential"/>{oneway}</way>'
            rel = (f'<relation id="900" version="1">'
                   '<member type="way" ref="100" role="from"/>'
                   '<member type="way" ref="200" role="via"/>'
                   '<member type="way" ref="300" role="to"/>'
                   f'<tag k="type" v="restriction"/><tag k="restriction" v="{kind}"/></relation>')
            if conditional:
                rel = rel.replace("</relation>",
                    '<tag k="restriction:conditional" v="none @ (Sa-Su)"/></relation>')
            xml = '<osm version="0.6">' + nodes + ''.join(way(*w) for w in ways) + rel + '</osm>'
            with tempfile.TemporaryDirectory() as tmp:
                source = Path(tmp) / 'via.osm'
                source.write_text(xml)
                return d.build(str(source))

        def arcs(g):
            return {(g["ways"][e[6]][0], g["nodes"][e[0]][2], g["nodes"][e[1]][2]): i
                    for i, e in enumerate(g["edges"])}

        only = make_graph("only_straight_on")
        e = arcs(only)
        a, v1, v2 = e[100, 1, 2], e[200, 2, 3], e[200, 3, 4]
        self.assertIn((a, e[400, 2, 6]), only["bans"])
        self.assertIn((a, v1, e[500, 3, 7]), only["seqs"])
        self.assertIn((a, v1, v2, e[600, 4, 8]), only["seqs"])
        self.assertNotIn((v1, e[500, 3, 7]), only["bans"],
                         "other approaches must not inherit the restriction")
        self.assertNotIn((e[700, 9, 3], e[500, 3, 7]), only["bans"])
        self.assertNotIn((a, v1, v2, e[300, 4, 5]), only["seqs"])
        no = make_graph("no_right_turn")
        n = arcs(no)
        self.assertIn((n[100, 1, 2], n[200, 2, 3], n[200, 3, 4], n[300, 4, 5]),
                      no["seqs"])
        self.assertNotIn((n[100, 1, 2], n[400, 2, 6]), no["bans"])
        # A timed companion must not silently remove a fixed no_* rule.
        mixed = make_graph("no_right_turn", conditional=True)
        x = arcs(mixed)
        self.assertIn((x[100, 1, 2], x[200, 2, 3], x[200, 3, 4], x[300, 4, 5]),
                      mixed["seqs"])
        # A time-only rule remains UNKNOWN without a verified clock parser.
        time_only = make_graph("", conditional=True)
        self.assertFalse(time_only["bans"])
        self.assertFalse(time_only["seqs"])


    def test_multi_via_way_restrictions_are_approach_specific(self):
        # The ways are a connected two-via-road chain. Deviations are real
        # directed side branches; a second approach joins at the middle.
        def graph(kind, break_join=False):
            nodes = "".join(
                f'<node id="{i}" version="1" lat="53.4" lon="{-2.2 + i * 0.001:.3f}"/>'
                for i in range(1, 15))
            ways = [
                (100, [1, 2]), (200, [2, 3, 4]),
                (201, [14, 5, 6] if break_join else [4, 5, 6]),
                (300, [6, 7]), (400, [2, 8]), (401, [3, 9]),
                (402, [4, 10]), (403, [5, 11]), (404, [6, 12]),
                (700, [13, 5]),
            ]
            def way(wid, refs):
                nd = "".join(f'<nd ref="{n}"/>' for n in refs)
                return (f'<way id="{wid}" version="1">{nd}'
                        '<tag k="highway" v="residential"/><tag k="oneway" v="yes"/></way>')
            relation = (
                '<relation id="900" version="1">'
                '<member type="way" ref="100" role="from"/>'
                '<member type="way" ref="200" role="via"/>'
                '<member type="way" ref="201" role="via"/>'
                '<member type="way" ref="300" role="to"/>'
                f'<tag k="type" v="restriction"/><tag k="restriction" v="{kind}"/>'
                '</relation>')
            with tempfile.TemporaryDirectory() as tmp:
                src = Path(tmp) / "multi-via.osm"
                src.write_text('<osm version="0.6">' + nodes +
                               "".join(way(*w) for w in ways) + relation + '</osm>')
                return d.build(str(src))

        def arcs(g):
            return {(g["ways"][e[6]][0], g["nodes"][e[0]][2], g["nodes"][e[1]][2]): i
                    for i, e in enumerate(g["edges"])}

        prohibited = graph("no_right_turn")
        n = arcs(prohibited)
        route = (n[100, 1, 2], n[200, 2, 3], n[200, 3, 4],
                 n[201, 4, 5], n[201, 5, 6], n[300, 6, 7])
        self.assertIn(route, prohibited["seqs"])
        self.assertNotIn((n[100, 1, 2], n[400, 2, 8]), prohibited["bans"],
                         "no_* through the full via chain must not ban other exits")

        mandatory = graph("only_straight_on")
        e = arcs(mandatory)
        a, v1, v2, v3, v4, tail = (
            e[100, 1, 2], e[200, 2, 3], e[200, 3, 4],
            e[201, 4, 5], e[201, 5, 6], e[300, 6, 7])
        self.assertIn((a, e[400, 2, 8]), mandatory["bans"])
        self.assertIn((a, v1, e[401, 3, 9]), mandatory["seqs"])
        self.assertIn((a, v1, v2, e[402, 4, 10]), mandatory["seqs"])
        self.assertIn((a, v1, v2, v3, e[403, 5, 11]), mandatory["seqs"])
        self.assertIn((a, v1, v2, v3, v4, e[404, 6, 12]), mandatory["seqs"])
        self.assertNotIn((a, v1, v2, v3, v4, tail), mandatory["seqs"])
        self.assertNotIn((e[700, 13, 5], e[403, 5, 11]), mandatory["bans"],
                         "only_* is not a global prohibition on unrelated approaches")
        self.assertTrue(all(q[0] == a for q in mandatory["seqs"]))
        disconnected = graph("no_right_turn", break_join=True)
        self.assertFalse(disconnected["seqs"], "missing join must remain unknown")
        self.assertFalse(disconnected["bans"])

    def test_conditional_only_turn_bans_are_conservative_and_approach_specific(self):
        # This fixture is not proof of legal clock/time interpretation.
        # Simple timed bans must not become an always-allowed illegal turn.
        def build(kind):
            nodes = "".join(
                f'<node id="{i}" version="1" lat="53.4" lon="{-2.2 + i * 0.001:.3f}"/>'
                for i in range(1, 6))
            ways = "".join(
                f'<way id="{wid}" version="1"><nd ref="{a}"/><nd ref="{b}"/>'
                '<tag k="highway" v="residential"/></way>'
                for wid, a, b in [(100, 1, 2), (200, 2, 3), (300, 2, 4), (400, 5, 2)])
            rel = ('<relation id="700" version="1">'
                   '<member type="way" ref="100" role="from"/>'
                   '<member type="node" ref="2" role="via"/>'
                   '<member type="way" ref="200" role="to"/>'
                   '<tag k="type" v="restriction"/>'
                   f'<tag k="restriction:conditional" v="{kind} @ (Mo-Fr 08:00-09:00)"/>'
                   '</relation>')
            with tempfile.TemporaryDirectory() as tmp:
                inp = Path(tmp) / "conditional.osm"
                inp.write_text('<osm version="0.6">' + nodes + ways + rel + '</osm>')
                return d.build(str(inp))
        def directed(g):
            return {(g["ways"][e[6]][0], g["nodes"][e[0]][2], g["nodes"][e[1]][2]): i
                    for i, e in enumerate(g["edges"])}
        no = build("no_right_turn")
        e = directed(no)
        self.assertIn((e[100, 1, 2], e[200, 2, 3]), no["bans"])
        self.assertNotIn((e[400, 5, 2], e[200, 2, 3]), no["bans"])
        self.assertEqual(1, no["conditional_only_turns_conservatively_enforced"])
        only = build("only_straight_on")
        e = directed(only)
        self.assertIn((e[100, 1, 2], e[300, 2, 4]), only["bans"])
        self.assertNotIn((e[100, 1, 2], e[200, 2, 3]), only["bans"])
        self.assertEqual(1, only["conditional_only_turns_conservatively_enforced"])
        unsupported = build("none")
        self.assertFalse(unsupported["bans"])
        self.assertEqual(0, unsupported["conditional_only_turns_conservatively_enforced"])

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
