"""Compare best match with closest reachable entrance for the same UK search queries.

Pure functions only: a run supplies the candidates for each query; nothing here fetches data.
A candidate is a dict with: name, match (0..1, higher is better), lat, lon, distance_m,
entrance ("reachable" | "unreachable" | "unknown") and data_date (ISO date the place record came from).
Unknown entrances are never chosen as the reachable entrance.
"""
from datetime import date
import json
from pathlib import Path

FIXTURE = Path(__file__).resolve().parent / 'fixtures' / 'uk_search_compare_50.json'
KINDS = {'postcode', 'house_postcode', 'poi', 'street'}
MODES = {'online', 'offline'}
FRESH_DAYS, AGING_DAYS = 90, 365


def load_queries(path=FIXTURE):
    return json.loads(Path(path).read_text(encoding='utf-8'))['queries']


def validate_queries(queries):
    problems = []
    if len(queries) != 50:
        problems.append(f'expected 50 queries, found {len(queries)}')
    ids = [q.get('id') for q in queries]
    if len(set(ids)) != len(ids):
        problems.append('duplicate query ids')
    for q in queries:
        if q.get('kind') not in KINDS:
            problems.append(f"{q.get('id')}: unknown kind {q.get('kind')!r}")
        if not str(q.get('text', '')).strip():
            problems.append(f"{q.get('id')}: empty text")
        if not set(q.get('modes', [])) <= MODES or not q.get('modes'):
            problems.append(f"{q.get('id')}: bad modes")
    return problems


def age_label(data_date, today):
    if not data_date:
        return 'unknown', None
    days = (today - date.fromisoformat(data_date)).days
    if days <= FRESH_DAYS:
        return 'fresh', days
    if days <= AGING_DAYS:
        return 'aging', days
    return 'stale', days


def compare(candidates, today):
    """Return best match, closest reachable entrance and the age of each record."""
    best = max(candidates, key=lambda c: c['match'], default=None)
    reachable = [c for c in candidates if c.get('entrance') == 'reachable']
    closest = min(reachable, key=lambda c: c['distance_m'], default=None)
    rows = []
    for c in (best, closest):
        if c is None:
            rows.append(None)
            continue
        label, days = age_label(c.get('data_date'), today)
        rows.append({'name': c['name'], 'age': label, 'age_days': days})
    return {
        'best_match': rows[0],
        'closest_reachable': rows[1],
        'same_place': best is not None and closest is not None and best['name'] == closest['name'],
    }
