"""TF-07: fail-closed single-session DMN offline evidence audit, NOT road truth.

This reads only receipts produced by Android instrumentation *in the current run*
and one current-run JUnit result for the specified test. No synthetic graph,
source-code fallback, unrelated map install, or Valhalla graph may qualify.

This first phase cannot itself supply the missing Android end-to-end test;
its output stays 'blocked' when that test/receipt is absent. Even a structurally
valid receipt is a self-report needing real source/artifact/voice corroboration.
"""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

STAGES=(
    "published_dmn_index",
    "dmn_piece_download",
    "production_dmn_install",
    "network_isolated",
    "production_dmn_route",
    "guidance_screen_visible",
    "spoken_instruction_observed",
    "network_still_isolated",
    "network_restored",
)
CLASS="com.drivemate.tests.cat04.PublishedDmnOfflineScreenTest"
METHOD="publishedDmnOfflineRouteShowsAndSpeaksSameSession"
RECEIPT="tf07-dmn-offline-single-session.json"
HEX40=re.compile(r"[a-f0-9]{40}\Z")
HEX64=re.compile(r"[a-f0-9]{64}\Z")
TAG=re.compile(r"dmn-uk-[0-9]{8}(?:-[0-9]+)?\Z")
PIECE=re.compile(r"dmn-[A-Za-z0-9_-]+\.dmnpack\Z")
SID=re.compile(r"[A-Za-z0-9_-]{16,96}\Z")


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def stage_claims(receipt):
    stages=receipt.get("stages")
    require(isinstance(stages,dict) and set(stages)==set(STAGES),
            "TF07_STAGE_SET_INVALID")
    last=-1
    for name in STAGES:
        row=stages[name]
        require(isinstance(row,dict) and row.get("session_id")==receipt["session_id"]
                and row.get("status")=="passed"
                and type(row.get("elapsed_ms")) is int
                and 0<=row["elapsed_ms"]<=24*60*60*1000
                and row["elapsed_ms"]>last,
                "TF07_STAGE_ORDER_OR_SESSION_INVALID:"+name)
        last=row["elapsed_ms"]


def verify_claim(receipt, run):
    """Validate a *claimed* observation only. Does not approve release/driving."""
    require(isinstance(receipt,dict) and receipt.get("schema")==1 and
            receipt.get("scope")=="dmn_published_uk_single_session" and
            receipt.get("engine")=="DriveMate Navigation (DMN)" and
            receipt.get("router")=="DmnRouter" and
            receipt.get("route_source")=="published_dmn_uk_piece",
            "TF07_NOT_DMN_PRODUCTION_SESSION")
    for key in ("app_sha","tests_sha"):
        require(isinstance(receipt.get(key),str) and HEX40.fullmatch(receipt[key])
                and receipt[key]==run[key],"TF07_REVISION_MISMATCH:"+key)
    for key in ("run_id","run_attempt"):
        require(type(receipt.get(key)) is int and
                receipt[key]==run[key],"TF07_RUN_MISMATCH:"+key)
    session=receipt.get("session_id")
    require(isinstance(session,str) and SID.fullmatch(session),
            "TF07_SESSION_ID_INVALID")
    require(receipt.get("country") in ("GB","GB-NIR") and
            receipt.get("test_class")==CLASS and receipt.get("test_method")==METHOD,
            "TF07_TEST_IDENTITY_MISMATCH")
    require(receipt.get("release_repository")=="phuk-prog/drivemate-map" and
            isinstance(receipt.get("release_tag"),str) and
            TAG.fullmatch(receipt["release_tag"]),
            "TF07_PUBLISHED_DMN_IDENTITY_INVALID")
    require(isinstance(receipt.get("index_sha256"),str) and
            HEX64.fullmatch(receipt["index_sha256"]),
            "TF07_INDEX_DIGEST_INVALID")
    pieces=receipt.get("dmn_pieces")
    require(isinstance(pieces,list) and len(pieces)>0 and len(pieces)<=8000,
            "TF07_DMN_PIECES_INVALID")
    names=set()
    for item in pieces:
        require(isinstance(item,dict) and
                isinstance(item.get("name"),str) and
                PIECE.fullmatch(item["name"]) and item["name"] not in names and
                type(item.get("bytes")) is int and 0<item["bytes"]<=200*1024*1024,
                "TF07_DMN_PIECES_INVALID")
        names.add(item["name"])
        h=item.get("published_sha256")
        require(isinstance(h,str) and HEX64.fullmatch(h) and
                item.get("installed_sha256")==h and item.get("read_sha256")==h,
                "TF07_DMN_PIECE_IDENTITY_MISMATCH")
    # The device test must separately observe transport; an airplane mode
    # shell command without a failed socket probe is not proof of isolation.
    network=receipt.get("network")
    require(isinstance(network,dict) and network.get("online_before") is True
            and network.get("offline_before_route") is True
            and network.get("offline_after_speech") is True
            and network.get("online_restored") is True
            and network.get("probe")=="android_socket_reachability",
            "TF07_NETWORK_PROBES_INCOMPLETE")
    route=receipt.get("route")
    require(isinstance(route,dict) and route.get("router")=="DmnRouter"
            and type(route.get("points")) is int and route["points"]>=2
            and type(route.get("steps")) is int and route["steps"]>=1,
            "TF07_ROUTE_NOT_VERIFIED")
    screen=receipt.get("screen")
    require(isinstance(screen,dict) and screen.get("actual_production_ui") is True
            and screen.get("instruction_visible") is True,
            "TF07_SCREEN_NOT_VERIFIED")
    speech=receipt.get("speech")
    require(isinstance(speech,dict) and
            speech.get("production_voice_pipeline") is True
            and speech.get("spoken_instruction_observed") is True,
            "TF07_SPEECH_NOT_VERIFIED")
    stage_claims(receipt)
    # This is only a self-reported evidence record, not audited device truth.
    return {
        "schema":1,"status":"record_structurally_consistent",
        "scope":"single_session_report_consistency_not_independent_acceptance",
        "session_id":session,"run_id":run["run_id"],
        "reported_stages":len(STAGES),
        "reported_pieces":len(pieces),
        "verified_android_junit":False,
        "source_bytes_independently_verified":False,
        "device_session_independently_verified":False,
        "offline_navigation_accepted":False,
        "road_law_truth":"unknown",
        "uk_coverage":"unknown",
    }


def junit_status(root):
    """Look up exact current-run Android instrumentation JUnit test identity."""
    root=Path(root)
    if not root.exists():
        return "missing"
    matches=[]
    for path in root.rglob("*.xml"):
        if path.is_symlink() or not path.is_file() or path.stat().st_size>20*1024*1024:
            raise ValueError("TF07_JUNIT_INVALID")
        try:
            doc=ET.parse(path)
        except ET.ParseError as exc:
            raise ValueError("TF07_JUNIT_INVALID") from exc
        for node in doc.getroot().iter("testcase"):
            if node.attrib.get("classname")==CLASS and node.attrib.get("name")==METHOD:
                matches.append(node)
    require(len(matches)<=1,"TF07_JUNIT_DUPLICATE")
    if not matches:
        return "missing"
    return "failed" if any(
        matches[0].find(f) is not None
        for f in ("failure","error","skipped")) else "passed"


def audit(media_dir,junit_dir,run):
    for key,length in (("app_sha",40),("tests_sha",40)):
        require(isinstance(run.get(key),str) and
                re.fullmatch(r"[a-f0-9]{"+str(length)+r"}",run[key]),
                "TF07_AUDIT_RUN_ID_INVALID")
    for key in ("run_id","run_attempt"):
        require(type(run.get(key)) is int and run[key]>0,
                "TF07_AUDIT_RUN_ID_INVALID")
    base=Path(media_dir)
    files=[] if not base.exists() else list(base.rglob(RECEIPT))
    require(len(files)<=1,"TF07_DUPLICATE_SESSION_RECEIPT")
    if not files:
        return {"schema":1,"status":"blocked","reason":"NO_SINGLE_SESSION_DMN_RECEIPT",
                "engine":"DriveMate Navigation (DMN)","observed_complete_sessions":0,
                "offline_navigation_accepted":False}
    path=files[0]
    require(not path.is_symlink() and path.is_file() and path.stat().st_size<=32*1024,
            "TF07_RECEIPT_FILE_INVALID")
    try:
        payload=json.loads(path.read_text(encoding="utf-8"))
    except (ValueError,UnicodeDecodeError,OSError) as exc:
        raise ValueError("TF07_RECEIPT_JSON_INVALID") from exc
    claim=verify_claim(payload,run)
    status=junit_status(junit_dir)
    if status!="passed":
        return {"schema":1,"status":"blocked",
                "reason":"DMN_ANDROID_JUNIT_"+status.upper(),
                "engine":"DriveMate Navigation (DMN)","observed_complete_sessions":0,
                "offline_navigation_accepted":False}
    return {**claim,"status":"record_and_junit_consistent",
            "verified_android_junit":True,
            "offline_navigation_accepted":False,
            "reason":"INDEPENDENT_SOURCE_DEVICE_VOICE_ATTESTATION_NOT_YET_CHECKED"}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--media-dir",required=True)
    p.add_argument("--junit-dir",required=True)
    p.add_argument("--app-sha",required=True)
    p.add_argument("--tests-sha",required=True)
    p.add_argument("--run-id",type=int,required=True)
    p.add_argument("--run-attempt",type=int,required=True)
    p.add_argument("--output",required=True)
    args=p.parse_args()
    report=audit(args.media_dir,args.junit_dir,vars(args)|{
        "app_sha":args.app_sha,"tests_sha":args.tests_sha,
        "run_id":args.run_id,"run_attempt":args.run_attempt})
    out=Path(args.output)
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,sort_keys=True,indent=2)+"\n")
    print(json.dumps({"status":report["status"],
                      "reason":report.get("reason","reported_evidence_only"),
                      "offline_navigation_accepted":False},sort_keys=True))


if __name__=="__main__":
    main()
