"""Verify original-key signed APK identity against the pinned signer."""
import argparse
import hashlib
import json
from pathlib import Path
import re

# Original-key signer certificate from verified build 40939044140, job 123681729697.
ORIGINAL_SIGNER = '0b6e8b39a57be35aa278488c908527e5490ca89379fd36e8de14b594050039ff'


def verify_identity(badging, certificates, expected_version):
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', certificates, re.M)
    if not package or package[1] != 'com.drivemate.app' or 'application-debuggable' in badging:
        raise ValueError('Expected non-debuggable DriveMate release package')
    if int(package[2]) != expected_version or package[3] != f'1.{expected_version}':
        raise ValueError('APK version differs from the expected build')
    if [value.lower() for value in signers] != [ORIGINAL_SIGNER]:
        raise ValueError('APK signer differs from the verified original signing key')
    return dict(package=package[1], version_code=int(package[2]), version_name=package[3],
                signer_sha256=ORIGINAL_SIGNER, original_key_verified=True, debuggable=False)


def main():
    parser = argparse.ArgumentParser()
    for name in ('apk', 'badging', 'certificates', 'app-sha', 'output'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.app_sha):
        raise ValueError('Exact app revision required')
    identity = verify_identity(Path(args.badging).read_text(), Path(args.certificates).read_text(), args.version_code)
    apk = Path(args.apk)
    identity.update(schema=1, app_sha=args.app_sha,
                    apk_file=apk.name, apk_bytes=apk.stat().st_size,
                    apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                    scope='private_original_key_signed_checkpoint', public_release_approved=False)
    Path(args.output).write_text(json.dumps(identity, indent=2) + '\n')
    print(json.dumps(identity, sort_keys=True))


if __name__ == '__main__':
    main()
