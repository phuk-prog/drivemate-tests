"""Never make a synthetic UK map while preparing the real Stockport app test."""
import hashlib
import json
import unittest
from prepare_stockport_live_test import one_region


def source():
    refs={kind:{"tag":"map-data-region-stockport-r123-a1",
                 "bytes":123,"sha256":"a"*64} for kind in ("map","manifest")}
    value={"schema":1,"coverage":"partial","regions":{
        "stockport":{"id":"stockport","name":"Stockport area",
                     "bounds":[-2.29,53.30,-1.98,53.47],**refs},
        "greater-manchester":{"id":"greater-manchester","name":"Greater Manchester",
                     "bounds":[-2.7,53.3,-1.9,53.7],
                     "map":{"tag":"map-data-region-greater-manchester-r123-a1","bytes":456,
                            "sha256":"b"*64},
                     "manifest":{"tag":"map-data-region-greater-manchester-r123-a1","bytes":89,
                            "sha256":"c"*64}}
    }}
    raw=json.dumps(value).encode()
    metadata={"tag_name":"map-regions","assets":[{
        "name":"catalog.json","size":len(raw),
        "digest":"sha256:"+hashlib.sha256(raw).hexdigest()}]}
    return raw,metadata


class PublishedStockportSelectionContract(unittest.TestCase):
    def test_pin_full_release_and_keep_only_real_region(self):
        raw,metadata=source()
        result=one_region(raw,metadata)
        self.assertEqual(["stockport"],list(result["regions"]))
        self.assertEqual(json.loads(raw)["regions"]["stockport"],result["regions"]["stockport"])
        self.assertEqual(hashlib.sha256(raw).hexdigest(),result["test_source"]["catalog_sha256"])
        self.assertFalse(result["test_source"]["offline_navigation_accepted"])

    def test_tampered_release_digest_rejected(self):
        raw,metadata=source()
        metadata["assets"][0]["digest"]="sha256:"+"0"*64
        with self.assertRaisesRegex(ValueError,"CATALOG_RELEASE_DIGEST_MISMATCH"):
            one_region(raw,metadata)

    def test_missing_published_stockport_rejected(self):
        raw,metadata=source()
        with self.assertRaisesRegex(ValueError,"LIVE_REGION_NOT_IN_CATALOG"):
            one_region(raw,metadata,key="not-published")

    def test_original_source_scope_not_overridden_by_subset(self):
        raw,metadata=source()
        value=json.loads(raw)
        item=value["regions"]["stockport"]
        value["regions"]["isle-of-man"]={**item,"id":"isle-of-man",
                                     "map":{**item["map"],"tag":"map-data-region-isle-of-man-r123-a1"},
                                     "manifest":{**item["manifest"],"tag":"map-data-region-isle-of-man-r123-a1"}}
        changed=json.dumps(value).encode()
        metadata["assets"][0]["size"]=len(changed)
        metadata["assets"][0]["digest"]="sha256:"+hashlib.sha256(changed).hexdigest()
        with self.assertRaisesRegex(ValueError,"LIVE_PUBLISHED_CATALOG_SCOPE_INVALID"):
            one_region(changed,metadata)


if __name__=="__main__":
    unittest.main()
