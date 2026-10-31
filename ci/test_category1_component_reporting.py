"""Category 1 component evidence must be classified without inventing acceptance.

A previously green app-unit test may be mapped into the current 1–24 report
only by an explicit reviewed class-to-category mapping. No new navigation
behaviour or road/legal truth is asserted by these reporting regressions.
"""
from pathlib import Path
import re
import unittest

from process_contract import manifest, programme_summary


ROOT = Path(__file__).resolve().parents[1]
UK_JOIN = "com.drivemate.app.data.UkJoinTest"
CATALOG = "com.drivemate.app.data.PublishedRegionCatalogTest"


def test_case(cls, status="passed", name="sourceBehaviour"):
    return {"class": cls, "name": name, "status": status}


def report(*tests):
    return programme_summary({
        "app_commit": "a" * 40,
        "public_tests_sha": "b" * 40,
        "checklist": {"App unit tests": {"tests": list(tests)}},
    })


class Category1ComponentReportingTest(unittest.TestCase):
    def test_only_reviewed_existing_test_sources_are_classified(self):
        mapping = manifest().get("component_category_map", {})
        self.assertEqual(1, mapping.get(UK_JOIN))
        self.assertEqual(1, mapping.get(CATALOG))
        for cls in (UK_JOIN, CATALOG):
            source = ROOT / "app-unit/java" / Path(*cls.split(".")).with_suffix(".kt")
            self.assertTrue(source.is_file(), f"Missing source: {source}")
            self.assertRegex(source.read_text(), r"@Test\s+fun\s+\w+")

    def test_existing_map_component_results_count_in_current_category_one(self):
        summary = report(test_case(UK_JOIN), test_case(CATALOG))
        cat1 = summary["categories"][0]
        self.assertEqual((2, 2, 0), (cat1["correct"], cat1["total"], cat1["failed"]))
        self.assertEqual(0, summary["unassigned"]["total"])

    def test_unknown_and_unmapped_components_are_not_falsely_passed_or_double_counted(self):
        summary = report(test_case(UK_JOIN, "unknown"), test_case(CATALOG),
                         test_case("com.drivemate.app.OtherUnitTest"))
        cat1 = summary["categories"][0]
        self.assertEqual((1, 2, 1), (cat1["correct"], cat1["total"], cat1["unknown"]))
        self.assertEqual("incomplete", cat1["status"])
        self.assertEqual(1, summary["unassigned"]["total"])
        self.assertEqual(3, cat1["total"] + summary["unassigned"]["total"])

    def test_historical_cat01_remains_current_turn_by_turn_not_current_map(self):
        summary = programme_summary({
            "app_commit": "a" * 40, "public_tests_sha": "b" * 40,
            "checklist": {"1 Turn-by-turn guidance: decisions": {
                "tests": [test_case("com.drivemate.tests.cat01.GuidanceDecisionsTest")]}},
        })
        self.assertEqual(0, summary["categories"][0]["total"])
        self.assertEqual(1, summary["categories"][6]["correct"])


if __name__ == "__main__":
    unittest.main()
