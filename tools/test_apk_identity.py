import unittest
from apk_identity import verify_identity


class ApkIdentityTest(unittest.TestCase):
    def test_original_key_and_release_package_are_required(self):
        from apk_identity import ORIGINAL_SIGNER
        package = "package: name='com.drivemate.app' versionCode='100012' versionName='1.100012'"
        cert = 'Signer #1 certificate SHA-256 digest: ' + ORIGINAL_SIGNER
        self.assertEqual(100012, verify_identity(package, cert, 100012)['version_code'])
        for bad in (cert.replace(ORIGINAL_SIGNER, '0' * 64), cert + '\nSigner #2 certificate SHA-256 digest: ' + ORIGINAL_SIGNER):
            with self.assertRaises(ValueError):
                verify_identity(package, bad, 100012)
        for bad in (package.replace('com.drivemate.app', 'com.drivemate.app.debug'),
                    package + '\napplication-debuggable', package.replace('100012', '100011')):
            with self.assertRaises(ValueError):
                verify_identity(bad, cert, 100012)


if __name__ == '__main__':
    unittest.main()
