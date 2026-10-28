"""The Android emulator runner executes each script line separately."""
import unittest
from pathlib import Path


class EmulatorWorkflowCommandTest(unittest.TestCase):
    def test_emulator_evidence_command_keeps_all_arguments_on_one_line(self):
        root=Path(__file__).resolve().parents[1]
        text=(root/'.github/workflows/test-app.yml').read_text()
        commands=[line.strip() for line in text.splitlines()
                  if 'python3 ../ci/emulator_evidence.py' in line]
        self.assertEqual(1,len(commands))
        self.assertIn('--source ../app-unit/resources/cat3-published-manchester-lanes-3.json',commands[0])
        self.assertIn('--out ../results/emulator-evidence-smoke.json',commands[0])
        self.assertFalse(commands[0].endswith('\\'),"Emulator runner must not split script arguments across commands")


if __name__=='__main__':
    unittest.main()
