"""Checks for the UK search comparison. Run: python3 -m unittest tools.test_search_comparison"""
from datetime import date
import unittest

from tools import search_comparison as sc

TODAY = date(2026, 11, 1)


class SearchComparisonTests(unittest.TestCase):
    def test_fixture_has_fifty_valid_queries(self):
        queries = sc.load_queries()
        self.assertEqual([], sc.validate_queries(queries))
        self.assertEqual(50, len(queries))
        self.assertEqual({'postcode', 'house_postcode', 'poi', 'street'}, {q['kind'] for q in queries})

    def test_best_match_and_closest_reachable_can_differ(self):
        candidates = [
            {'name': 'Kerb-side shop', 'match': 0.95, 'distance_m': 40, 'entrance': 'unreachable', 'data_date': '2026-10-01'},
            {'name': 'Car park entrance', 'match': 0.80, 'distance_m': 120, 'entrance': 'reachable', 'data_date': '2026-10-01'},
        ]
        result = sc.compare(candidates, TODAY)
        self.assertEqual('Kerb-side shop', result['best_match']['name'])
        self.assertEqual('Car park entrance', result['closest_reachable']['name'])
        self.assertFalse(result['same_place'])

    def test_unknown_entrance_is_never_chosen_as_reachable(self):
        candidates = [{'name': 'Unsurveyed', 'match': 0.9, 'distance_m': 5, 'entrance': 'unknown', 'data_date': None}]
        result = sc.compare(candidates, TODAY)
        self.assertIsNone(result['closest_reachable'])
        self.assertEqual('unknown', result['best_match']['age'])

    def test_data_age_labels(self):
        self.assertEqual(('fresh', 30), sc.age_label('2026-10-02', TODAY))
        self.assertEqual(('aging', 170), sc.age_label('2026-05-15', TODAY))
        self.assertEqual('stale', sc.age_label('2024-01-01', TODAY)[0])

    def test_future_dates_are_unknown_and_ties_break_by_distance_then_name(self):
        self.assertEqual(('unknown', None), sc.age_label('2026-11-05', TODAY))
        tied = [{'name': 'Zed', 'match': 0.5, 'distance_m': 30, 'entrance': 'reachable', 'data_date': None},
                {'name': 'Able', 'match': 0.5, 'distance_m': 30, 'entrance': 'reachable', 'data_date': None}]
        self.assertEqual('Able', sc.compare(tied, TODAY)['best_match']['name'])
        self.assertEqual('Able', sc.compare(tied, TODAY)['closest_reachable']['name'])

    def test_validation_reports_bad_rows(self):
        bad = [{'id': 'a', 'kind': 'shop', 'text': '', 'modes': []}]
        problems = sc.validate_queries(bad)
        self.assertTrue(any('unknown kind' in p for p in problems))
        self.assertTrue(any('empty text' in p for p in problems))


if __name__ == '__main__':
    unittest.main()
