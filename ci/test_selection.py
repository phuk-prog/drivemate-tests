"""Explicit related test batches; partial runs never produce full-release evidence."""
import os
from pathlib import Path
import re
import shutil
from process_contract import manifest


def selected(path, batch=None):
    batch=batch or os.environ.get('DRIVEMATE_TEST_BATCH','all')
    m=manifest(); batches={b['id']:set(b['categories']) for b in m['batches']}
    if batch!='all' and batch not in batches:
        raise ValueError('UNKNOWN_BATCH:' + batch)
    path=Path(path)
    if path.parts[0]=='app-unit': return True
    old=re.match(r'^(\d+)-',path.parts[1]) if len(path.parts)>2 and path.parts[0]=='categories' else None
    current=m['test_source_directory_category_map'].get(str(int(old.group(1)))) if old else None
    if current is None: raise ValueError('UNMAPPED_CATEGORY_SOURCE:' + str(path))
    return batch=='all' or current in batches[batch]


def install(app):
    app=Path(app)
    for kind in ('test','androidTest'):
        target=app/'app/src'/kind
        if target.exists(): shutil.rmtree(target)
        (target/'java').mkdir(parents=True)
    for source in sorted(Path('app-unit/java').rglob('*.kt')):
        target=app/'app/src/test/java'/source.relative_to('app-unit/java')
        target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,target)
    shutil.copytree('app-unit/resources',app/'app/src/test/resources',dirs_exist_ok=True)
    for source in sorted(Path('categories').rglob('*.kt')):
        if not selected(source): continue
        kind=source.parts[2]
        if kind not in ('law','decisions','screen'): raise ValueError('UNKNOWN_TEST_KIND:' + str(source))
        target=app/'app/src'/('androidTest' if kind=='screen' else 'test')/'java/public_categories'/source.relative_to('categories')
        target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,target)
    assets=app/'app/src/androidTest/assets'; assets.mkdir(parents=True,exist_ok=True)
    shutil.copy2('app-unit/resources/cat3-published-manchester-lanes-3.json',assets)
    output=os.environ.get('GITHUB_OUTPUT')
    if output:
        screens=list((app/'app/src/androidTest/java').rglob('*.kt'))
        with open(output,'a') as stream:
            stream.write('has_screen='+('true' if screens else 'false')+'\n')


if __name__=='__main__':
    import sys
    install(sys.argv[1])
