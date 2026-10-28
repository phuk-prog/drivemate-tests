"""Real Android-emulator file transport receipt for a frozen UK source fixture.

This verifies only ADB install/readback bytes on the emulator. The fixture is
an archived UK observation: not a freshly downloaded map release, not a
production cache, and never evidence of offline navigation acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


class AndroidFileDevice:
    PATH="/data/local/tmp/drivemate-archived-uk-fixture.json"

    def adb(self,*args,timeout=20):
        result=subprocess.run(["adb",*args],capture_output=True,timeout=timeout,check=False)
        if result.returncode:
            raise OSError("ADB_FILE_COMMAND_FAILED")
        return result.stdout

    def install(self,data):
        with tempfile.NamedTemporaryFile(prefix="drivemate-source-",suffix=".json") as tmp:
            tmp.write(data)
            tmp.flush()
            self.adb("push",tmp.name,self.PATH,timeout=30)
        digest=self.adb("shell","sha256sum",self.PATH).decode("ascii","replace")
        match=re.match(r"^([0-9a-f]{64})\s",digest)
        if match is None:
            raise OSError("ADB_FILE_HASH_UNAVAILABLE")
        return match.group(1)

    def readback(self):
        return self.adb("exec-out","cat",self.PATH,timeout=25)

    def remove(self):
        self.adb("shell","rm","-f",self.PATH)


def capture(source_file,output_file,device):
    raw=Path(source_file).read_bytes()
    digest=sha(raw)
    result={
        "schema":1,"scope":"emulator_fixture_transport_only",
        "source_kind":"archived_real_uk_fixture",
        "source_sha256":digest,"device_installed_sha256":None,
        "device_readback_sha256":None,
        "status":"inconclusive","reason_code":"DEVICE_OPERATION_FAILED",
        "published_release_downloaded":False,
        "production_map_cache_loaded":False,
        "offline_acceptance":False
    }
    try:
        result["device_installed_sha256"]=device.install(raw)
        result["device_readback_sha256"]=sha(device.readback())
        if result["device_installed_sha256"]!=digest:
            result["reason_code"]="DEVICE_INSTALL_MISMATCH"
        elif result["device_readback_sha256"]!=digest:
            result["reason_code"]="DEVICE_READBACK_MISMATCH"
        else:
            result["status"]="observed"
            result["reason_code"]=None
    except (OSError,subprocess.TimeoutExpired,ValueError):
        result["status"]="inconclusive"
        result["reason_code"]="DEVICE_OPERATION_FAILED"
    finally:
        try:
            device.remove()
        except (OSError,subprocess.TimeoutExpired):
            result["status"]="inconclusive"
            result["reason_code"]="DEVICE_CLEANUP_FAILED"
        target=Path(output_file)
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    return result


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",required=True)
    parser.add_argument("--out",required=True)
    opts=parser.parse_args()
    result=capture(opts.source,opts.out,AndroidFileDevice())
    print("emulator fixture readback: "+result["status"]+
          (" ("+result["reason_code"]+")" if result["reason_code"] else ""))
