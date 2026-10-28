"""Compare the existing Manchester fixture to archived, fingerprinted public source observations.

This detects fixture drift; it does not verify road law or reconstruct the full original square.
"""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def load_source(directory):
    directory = Path(directory)
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest.get('schema') != 1 or set(manifest.get('files', {})) != {'source-samples.json', 'source-audit.json'}:
        raise ValueError('invalid_provenance_manifest')
    documents = {}
    for name, metadata in manifest['files'].items():
        raw = (directory / name).read_bytes()
        if len(raw) != metadata['bytes'] or hashlib.sha256(raw).hexdigest() != metadata['sha256']:
            raise ValueError('provenance_integrity_mismatch')
        documents[name] = json.loads(raw)
    samples = documents['source-samples.json']
    if samples['source_sha256'] != documents['source-audit.json']['lane_square_sha256']:
        raise ValueError('source_digest_mismatch')
    return samples


def verify(fixture, source):
    if fixture.get('schema') != 1 or source.get('schema') != 1:
        raise ValueError('unsupported_fixture_schema')
    digest = fixture.get('source_sha256')
    if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest) or digest != source.get('source_sha256'):
        raise ValueError('source_digest_mismatch')
    original = source.get('ways')
    ways = fixture.get('ways')
    if not isinstance(original, list) or not isinstance(ways, list) or not ways:
        raise ValueError('missing_source_ways')
    index = {}
    for way in original:
        if not isinstance(way, dict) or type(way.get('id')) is not int or way['id'] in index:
            raise ValueError('duplicate_way_or_invalid_source')
        index[way['id']] = way
    seen = set()
    for way in ways:
        if not isinstance(way, dict) or type(way.get('id')) is not int:
            raise ValueError('invalid_fixture_way')
        if way['id'] in seen:
            raise ValueError('duplicate_way')
        seen.add(way['id'])
        if way != index.get(way['id']):
            raise ValueError('source_way_mismatch:' + str(way['id']))
    return {'correct': len(ways), 'total': len(ways), 'failed': 0,
            'legal_truth': 'unknown', 'scope': 'frozen source observations only',
            'source_sha256': digest}


def main():
    source = load_source(ROOT / 'provenance/manchester-lanes')
    fixture = json.loads((ROOT / 'app-unit/resources/cat3-published-manchester-lanes-3.json').read_text())
    result = verify(fixture, source)
    out = ROOT / 'results/frozen-source-provenance.json'
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
