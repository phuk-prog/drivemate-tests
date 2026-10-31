"""Code-backed test prerequisites from imports; unobserved categories stay explicit."""
import hashlib
from pathlib import Path
import re
from process_contract import manifest

COMPONENTS = {
    1:'OfflineStorage RegionalMapDownloader MapPack OwnMap RegionalMapStyleComposer RegionMosaic',
    2:'LaneDb MapData UkJoin DmnPack DmnRouter Route RouteStep Router Dmn RouteChoices',
    3:'GpsFilter LocationTracker MotionHeading PositionUncertainty',
    4:'Geocoder OfflineSearch',
    8:'NavigationEngine TurnInstructions Wording',
    9:'LanePolicy LanePlan LaneTiming LaneGuidance',
    10:'Voice Speech SpokenGuidance VoiceQueue',
    11:'OffRouteRule ReroutePolicy',
    12:'SpeedLimitMatcher LimitComparison',
    13:'PositionCameraPolicy ImmersiveMotion',
    15:'ArrivalPolicy ArrivalApproachInstruction',
    16:'JunctionScene ImmersiveJunction ImmersiveMap',
    17:'JunctionViewPolicy JunctionPlan JunctionModel',
    18:'RoadAlerts RoadAlertPolicy',
    19:'TrafficPolicies TrafficDetails NationalHighways',
    20:'SessionTasks ApplicationContainer OperationStatus BackupManager BackupSchema SettingsRepository SettingsSections',
    21:'CarBridge',
    22:'PostDriveReview PostDriveController TripStore',
    23:'SharedMapCorrections LaneCorrections',
}


def build(app_root,test_root):
    m=manifest(); app_root=Path(app_root); test_root=Path(test_root)
    files=list((app_root/'app/src/main/java').rglob('*.kt'))
    if not files: raise ValueError('APP_SOURCE_UNAVAILABLE')
    symbols={}; domains={s:n for n,words in COMPONENTS.items() for s in words.split()}
    for path in files:
        text=path.read_text(); package=re.search(r'^package\s+([\w.]+)',text,re.M)
        if not package: continue
        for name in re.findall(r'\b(?:class|object|interface)\s+(\w+)',text):
            symbols[package.group(1)+'.'+name]=(path,hashlib.sha256(path.read_bytes()).hexdigest())
    edges=[]; observed=set(); unresolved=set()
    for source in sorted((test_root/'categories').rglob('*.kt')):
        old=re.match(r'(\d+)-',source.relative_to(test_root/'categories').parts[0])
        category=m['test_source_directory_category_map'].get(str(int(old.group(1)))) if old else None
        if not category: continue
        observed.add(category)
        for imported in re.findall(r'^import\s+(com\.drivemate\.app\.[\w.]+)',source.read_text(),re.M):
            record=symbols.get(imported); component=domains.get(imported.rsplit('.',1)[-1])
            if not record or component is None:
                unresolved.add(imported); continue
            if component==category: continue
            path,digest=record
            row={'category':category,'prerequisite':component,'test':str(source.relative_to(test_root)),
                 'production_file':str(path.relative_to(app_root)),'symbol':imported,'source_sha256':digest}
            if row not in edges: edges.append(row)
    return {'schema':1,'scope':'Observed public-test imports; runtime/transitive dependencies are not inferred',
            'nodes':[dict(c,status='observed' if c['id'] in observed else 'not_observed') for c in m['categories']],
            'edges':edges,'unclassified_imports':sorted(unresolved),
            'execution_rule':'Prerequisite components may be checked within the active category; this does not reorder the register'}
