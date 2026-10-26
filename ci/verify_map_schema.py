"""Run the real Planetiler schema verifier; retain only bounded JUnit outcome for the summary."""
import re
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

jar, schema, destination = sys.argv[1:]
result = subprocess.run(['java', '-jar', jar, 'verify', schema], capture_output=True, text=True)
lines = re.sub(r'\x1b\[[0-9;]*m', '', result.stdout + '\n' + result.stderr).splitlines()
summary = next((line.strip() for line in reversed(lines) if re.search(r'\d+ passed', line)), '')
passed = result.returncode == 0 and bool(summary) and 'failed' not in summary.lower()
suite = ET.Element('testsuite', name='Mapped height provenance', tests='1', failures='0' if passed else '1')
case = ET.SubElement(suite, 'testcase', classname='com.drivemate.tests.cat05.MapSchemaDecisionsTest', name='q5_8_mapSchemaUsesMappedMetresAndNeverInventsHeight')
if not passed:
    ET.SubElement(case, 'failure', message='Map schema verification failed or produced no passing summary; no map artifacts were published.')
target = Path(destination)
target.parent.mkdir(parents=True, exist_ok=True)
ET.ElementTree(suite).write(target, encoding='unicode')
print('Map height schema: ' + ('passed' if passed else 'failed'))
