"""Build-context contracts; fixtures are not real APK acceptance."""
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile
from apk_build_context import inspect_context


class ApkBuildContextTests(unittest.TestCase):
    def test_binds_build_and_scans_packaged_assets_without_private_urls(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'app.apk'
            with ZipFile(apk, 'w') as archive:
                archive.writestr('assets/config.json', '{"endpoint":"https://assets.example.org/v1?token=private"}')
                archive.writestr('lib/x86_64/librouter.so', b'private-native')
                archive.writestr('resources.arsc', b'https://resource.example.org/x\x00')
            generated = root / 'app/build/generated/source/buildConfig/debug/com/example/BuildConfig.java'
            generated.parent.mkdir(parents=True)
            generated.write_text('String BASE="https://build.example.org/";')
            source = root / 'app/src/main/java/Provider.kt'
            source.parent.mkdir(parents=True)
            source.write_text('val url = "${BuildConfig.BASE}/$name"')
            report = root / 'source.json'
            report.write_text('{"scope":"production_source_static_connection_inventory"}')
            value = inspect_context(apk, root, report, 'a'*40, 'b'*40, '123', '1')
            self.assertEqual('a'*40, value['app_sha'])
            self.assertEqual('b'*40, value['test_sha'])
            self.assertEqual('123', value['run_id'])
            self.assertEqual(64, len(value['source_report_sha256']))
            self.assertEqual({'assets.example.org': 1, 'resource.example.org': 1}, value['packaged_host_counts'])
            self.assertEqual({'build.example.org': 1}, value['build_config_host_counts'])
            self.assertEqual(1, value['native_library_count'])
            self.assertEqual(1, value['runtime_construction_source_files'])
            self.assertFalse(value['native_connections_verified'])
            self.assertFalse(value['dynamic_connections_verified'])
            self.assertNotIn('private', json.dumps(value))

    def test_oversized_assets_remain_unknown_and_native_is_not_scanned(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'app.apk'
            with ZipFile(apk, 'w') as archive:
                archive.writestr('assets/large.bin', b'x' * (1024*1024+1))
                archive.writestr('lib/arm64-v8a/libnative.so', b'https://native.example.org/')
            report = root / 'source.json'
            report.write_text('{}')
            value = inspect_context(apk, root, report, 'a'*40, 'b'*40, '123', '1')
            self.assertEqual(1, value['packaged_entries_skipped_unknown'])
            self.assertEqual(0, value['packaged_entries_scanned'])
            self.assertEqual({}, value['packaged_host_counts'])
            self.assertEqual(0, value['build_config_files_scanned'])
            self.assertFalse(value['dynamic_connections_verified'])

    def test_invalid_build_identity_fails_closed(self):
        with self.assertRaisesRegex(ValueError, 'INVALID_BUILD_IDENTITY'):
            inspect_context('missing', '.', 'missing', 'main', 'b'*40, '123', '1')
