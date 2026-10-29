"""Adversarial validator records only; none is authenticated legal road truth."""
import copy
import unittest
from process_contract import validate_acceptance


class LegalTruthBindingTest(unittest.TestCase):
    def record(self):
        return dict(app_sha='a'*40, test_sha='b'*40, map_sha256='c'*64,
                    input_sha256='d'*64, case_id='validator-only', mode='online',
                    country='GB', source_kind='published_real_uk',
                    claim='legal_manoeuvre', action_id='from-1-via-2-to-3',
                    evaluated_utc='2026-10-29T00:00:00Z',
                    stages={s:dict(status='passed', artifact_sha256='e'*64)
                            for s in ('source','loader','session','screen')},
                    legal_truth=dict(status='verified',url='https://www.stockport.gov.uk/test-only',
                                     document_sha256='f'*64,observed_utc='2026-10-28T00:00:00Z'))

    def bound_record(self):
        e=self.record()
        e['legal_truth'].update(schema=2,document_id='validator-only-order',
            document_version='test-version',applicable_from_utc='2026-01-01T00:00:00Z',
            applicable_until_utc='2027-01-01T00:00:00Z',
            binding={k:e[k] for k in ('app_sha','test_sha','map_sha256','input_sha256',
                                     'case_id','country','claim','action_id')})
        return e

    def test_old_unbound_official_document_cannot_authorize_movement(self):
        with self.assertRaisesRegex(ValueError,'INDEPENDENT_TRUTH_REQUIRED'):
            validate_acceptance(self.record())

    def test_cross_case_action_and_version_replay_rejected(self):
        for key in ('app_sha','test_sha','map_sha256','input_sha256','case_id','country','claim','action_id'):
            with self.subTest(key=key):
                e=self.bound_record(); e['legal_truth']['binding'][key]='other'
                with self.assertRaisesRegex(ValueError,'INDEPENDENT_TRUTH_REQUIRED'):
                    validate_acceptance(e)

    def test_expired_and_future_applicability_rejected(self):
        for key,value in [('applicable_until_utc','2026-10-28T00:00:00Z'),
                          ('applicable_from_utc','2026-10-30T00:00:00Z')]:
            e=self.bound_record(); e['legal_truth'][key]=value
            with self.assertRaisesRegex(ValueError,'INDEPENDENT_TRUTH_REQUIRED'):
                validate_acceptance(e)

    def test_schema_validation_does_not_claim_authenticated_truth(self):
        self.assertEqual('evidence_record_valid',validate_acceptance(self.bound_record())['status'])

    def test_unknown_evidence_cannot_become_green(self):
        e=self.bound_record(); e['legal_truth']['status']='unknown'
        with self.assertRaisesRegex(ValueError,'INDEPENDENT_TRUTH_REQUIRED'):
            validate_acceptance(e)


if __name__ == '__main__': unittest.main()
