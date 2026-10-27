"""Benchmark integrity regressions: a rank must mean a real head-to-head comparison.

These tests use artificial score records only to check the validator; they do not
assert UK road truth, actual engine performance, or real navigation test results.
"""
import unittest
from process_contract import validate_benchmark


def entry(engine, *, correct=8, total=10, unknown=0, skipped=0, provisional=0):
    return {
        "app_sha": "a" * 40, "test_sha": "b" * 40,
        "map_sha256": "c" * 64, "input_sha256": "d" * 64,
        "case_id": "uk-frozen-comparison-fixture",
        "country": "GB", "source_kind": "published_real_uk",
        "mode": "offline", "engine": engine,
        "total": total, "correct": correct,
        "unknown": unknown, "skipped": skipped, "provisional": provisional,
    }


class BenchmarkRankingIntegrityTest(unittest.TestCase):
    def test_one_complete_engine_does_not_get_first_place(self):
        result = validate_benchmark([entry("DMN", correct=10)])
        self.assertEqual(10, result[0]["score"])
        self.assertIsNone(result[0]["rank"])

    def test_one_complete_and_one_incomplete_engine_are_not_ranked(self):
        result = validate_benchmark([
            entry("DMN", correct=10), entry("Reference", correct=9, unknown=1)
        ])
        self.assertEqual(["complete", "incomplete"], [r["coverage"] for r in result])
        self.assertTrue(all(r["rank"] is None for r in result))

    def test_duplicate_engine_cannot_be_counted_as_a_competitor(self):
        with self.assertRaisesRegex(ValueError, "DUPLICATE_BENCHMARK_ENGINE"):
            validate_benchmark([entry("DMN"), entry(" dmn ")])

    def test_missing_engine_identity_cannot_claim_a_comparison(self):
        for value in ("", "   ", None):
            with self.subTest(engine=value):
                with self.assertRaisesRegex(ValueError, "BENCHMARK_ENGINE_REQUIRED"):
                    validate_benchmark([entry(value), entry("Reference")])

    def test_two_distinct_complete_engines_get_comparable_ranks(self):
        result = validate_benchmark([
            entry("DMN", correct=8), entry("Reference", correct=9)
        ])
        self.assertEqual([2, 1], [r["rank"] for r in result])
        self.assertEqual([8, 9], [r["score"] for r in result])

    def test_incomplete_engine_never_inherits_rank_from_comparable_pair(self):
        result = validate_benchmark([
            entry("DMN", correct=8), entry("Reference", correct=9),
            entry("Unknown", correct=9, provisional=1),
        ])
        self.assertEqual([2, 1, None], [r["rank"] for r in result])


if __name__ == "__main__":
    unittest.main()
