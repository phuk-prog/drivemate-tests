import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ProcessContractTest(unittest.TestCase):
    def setUp(self):
        p = ROOT / 'ci/process_contract.py'
        self.assertTrue(p.is_file(), 'Executable testing-process contract is missing')
        spec = importlib.util.spec_from_file_location('contract', p)
        self.c = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.c)

    def document(self):
        return {'app_commit': 'a'*40, 'public_tests_sha': 'b'*40,
                'checklist': {'3 Lane guidance: decisions': {'tests': [
                    {'class': 'com.drivemate.tests.cat03.LaneChecks', 'name': 'known', 'status': 'passed'},
                    {'class': 'com.drivemate.tests.cat03.LaneChecks', 'name': 'missing', 'status': 'unknown'},
                    {'class': 'com.drivemate.tests.cat03.LaneChecks', 'name': 'dated', 'status': 'provisional'}]}}}

    def test_all_current_categories_are_reported_without_invented_passes(self):
        report = self.c.programme_summary(self.document())
        self.assertEqual(list(range(1,24)), [r['id'] for r in report['categories']])
        lanes = report['categories'][7]
        self.assertEqual((1,3,0,1,1), tuple(lanes[k] for k in ('correct','total','failed','unknown','provisional')))
        self.assertEqual('incomplete', lanes['status'])
        self.assertEqual('not_tested', report['categories'][0]['status'])
        self.assertEqual('NO_RECORDED_CHECKS', report['categories'][0]['reason_code'])

    def test_later_historical_category_labels_are_mapped(self):
        d=self.document(); d['checklist']={'cat11: screen':{'tests':[{'name':'camera','status':'passed'}]}}
        r=self.c.programme_summary(d)
        self.assertEqual(1,r['categories'][10]['correct'])
        self.assertEqual(0,r['unassigned']['total'])

    def test_unassigned_component_results_do_not_become_category_acceptance(self):
        d=self.document(); d['checklist']={'App unit tests':{'tests':[{'class':'com.drivemate.app.data.UkJoinTest','name':'component','status':'passed'}]}}
        r=self.c.programme_summary(d)
        self.assertEqual(1,r['unassigned']['correct'])
        self.assertTrue(all(c['status']=='not_tested' for c in r['categories']))

    def test_invalid_status_cannot_be_green(self):
        d=self.document(); d['checklist']['3 Lane guidance: decisions']['tests'][0]['status']='maybe'
        with self.assertRaisesRegex(ValueError,'INVALID_RESULT_STATUS'):
            self.c.programme_summary(d)

    def test_safety_failures_block_release_separately_from_quality(self):
        r=self.c.release_assessment([{'id':'illegal-turn','severity':'safety_critical','status':'failed'},
                                     {'id':'camera','severity':'quality','status':'failed'},
                                     {'id':'lane-truth','severity':'quality','status':'unknown'}])
        self.assertFalse(r['release_allowed'])
        self.assertEqual(['illegal-turn'],r['safety_blockers'])
        self.assertEqual(['camera'],r['quality_findings'])
        self.assertEqual(['lane-truth'],r['unknowns'])

    def evidence(self):
        return {'app_sha':'a'*40,'test_sha':'b'*40,'map_sha256':'c'*64,
                'input_sha256':'d'*64,'case_id':'osm-way-930354',
                'mode':'offline','country':'GB','source_kind':'published_real_uk',
                'stages':{s:{'artifact_sha256':'e'*64,'status':'passed'} for s in ('source','loader','session','screen')},
                'legal_truth':{'status':'unknown'}}

    def test_components_cannot_claim_source_to_screen_acceptance(self):
        e=self.evidence(); del e['stages']['session']
        with self.assertRaisesRegex(ValueError,'MISSING_ACCEPTANCE_STAGE'):
            self.c.validate_acceptance(e)

    def test_acceptance_requires_immutable_inputs(self):
        e=self.evidence(); e['map_sha256']='latest'
        with self.assertRaisesRegex(ValueError,'INVALID_IMMUTABLE_ID'):
            self.c.validate_acceptance(e)

    def test_unknown_legal_truth_does_not_authorize_lane_advice(self):
        e=self.evidence(); e['claim']='legal_lane_advice'
        with self.assertRaisesRegex(ValueError,'INDEPENDENT_TRUTH_REQUIRED'):
            self.c.validate_acceptance(e)

    def test_passing_pipeline_can_be_recorded_without_legal_claim(self):
        self.assertEqual('pipeline_only', self.c.validate_acceptance(self.evidence())['scope'])

    def test_identical_retest_keeps_test_map_and_input_frozen(self):
        a=self.evidence(); b=copy.deepcopy(a); b['app_sha']='f'*40
        self.c.validate_retest(a,b)
        b['input_sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'RETEST_INPUT_DRIFT'):
            self.c.validate_retest(a,b)

    def test_benchmarks_refuse_different_routes_or_online_offline_modes(self):
        a=self.evidence(); b=copy.deepcopy(a); b['mode']='online'
        with self.assertRaisesRegex(ValueError,'INCOMPARABLE_BENCHMARK'):
            self.c.validate_benchmark([a,b])

    def test_missing_competitor_coverage_remains_incomplete(self):
        a=self.evidence(); a.update(engine='DMN',correct=8,total=10,unknown=2)
        r=self.c.validate_benchmark([a])
        self.assertEqual('incomplete',r[0]['coverage'])
        self.assertIsNone(r[0]['rank'])

    def test_skipped_benchmark_cases_cannot_receive_a_rank(self):
        a=self.evidence(); a.update(engine='DMN',correct=8,total=10,unknown=0,skipped=2)
        r=self.c.validate_benchmark([a])
        self.assertEqual('incomplete',r[0]['coverage'])
        self.assertIsNone(r[0]['rank'])

    def test_unidentified_routes_cannot_receive_a_rank(self):
        a=self.evidence(); a.update(engine='DMN',correct=10,total=10,unknown=0); del a['case_id']
        with self.assertRaisesRegex(ValueError,'CASE_ID_REQUIRED'):
            self.c.validate_benchmark([a])

    def test_benchmark_denominator_cannot_change_between_engines(self):
        a=self.evidence(); a.update(engine='DMN',correct=8,total=10,unknown=0)
        b=copy.deepcopy(a); b.update(engine='competitor',total=9)
        with self.assertRaisesRegex(ValueError,'eligible_denominator'):
            self.c.validate_benchmark([a,b])

    def test_different_provider_map_builds_are_disclosed_not_falsely_identical(self):
        a=self.evidence(); a.update(engine='DMN',correct=8,total=10,unknown=0)
        b=copy.deepcopy(a); b.update(engine='competitor',correct=9,map_sha256='f'*64)
        r=self.c.validate_benchmark([a,b])
        self.assertEqual((2,1),tuple(x['rank'] for x in r))
        self.assertEqual(('c'*64,'f'*64),tuple(x['map_sha256'] for x in r))

    def test_ci_retest_reader_rejects_changed_frozen_input(self):
        from process_preflight import retest_records
        import json
        a=self.evidence(); b=copy.deepcopy(a); b['input_sha256']='f'*64
        with tempfile.TemporaryDirectory() as d:
            Path(d,'record.json').write_text(json.dumps({'schema':1,'before':a,'after':b}))
            with self.assertRaisesRegex(ValueError,'RETEST_INPUT_DRIFT'):
                retest_records(d)


if __name__=='__main__': unittest.main()
