"""Category 1: user-facing map-data notices must resolve to the real publisher.

Checks production DataCredits source and an independently checked-out publisher
notice file. No road geometry or legal-clearance claim is made.
"""
import os
from pathlib import Path
import re
import unittest

APP = Path(os.environ.get("DRIVEMATE_APP_SRC", "app-src"))
MAP = Path(os.environ.get("DRIVEMATE_MAP_SRC", "map-src"))
EXPECTED = "https://github.com/phuk-prog/drivemate-map/blob/main/licenses/NOTICE.md"


class Category1CreditsLinkTest(unittest.TestCase):
    def test_full_notices_link_points_to_actual_publisher_notice(self):
        source = (APP / "app/src/main/java/com/drivemate/app/data/DataCredits.kt").read_text()
        match = re.search(r'const val NOTICE_URL\s*=\s*"([^"]+)"', source)
        self.assertIsNotNone(match, "Production DataCredits.NOTICE_URL missing")
        self.assertEqual(EXPECTED, match.group(1))
        self.assertRegex(source, r'"Full notices"\s+to\s+NOTICE_URL')
        notice = MAP / "licenses" / "NOTICE.md"
        self.assertTrue(notice.is_file(), "Actual publisher NOTICE.md absent")
        self.assertIn("OpenStreetMap", notice.read_text())


if __name__ == "__main__":
    unittest.main()
