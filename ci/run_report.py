#!/usr/bin/env python3
"""One picture page per emulator run: every test, pass or fail, what it
measured, and the screenshots the app really showed. Built from the run's own full-app.json and
screenshots; written as a page body (title, style, content) for the Artifact viewer or a browser.

Usage: run_report.py FULL_APP_JSON MEDIA_DIR OUT_HTML
"""
import base64
from datetime import datetime, timezone
import html
import io
import json
from pathlib import Path
import re
import sys
from zoneinfo import ZoneInfo

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the workflow installs Pillow
    Image = None

THUMB_W = 260
# Verdict values worth reading; long series (samples, logs) are summarised, not printed.
SKIP_KEYS = {"event", "samples", "immersive_log", "map_events"}


def thumb(path):
    if Image is None:
        return None
    im = Image.open(path).convert("RGB")
    im = im.resize((THUMB_W, max(1, int(im.height * THUMB_W / im.width))))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=72)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def words(name):
    """motorwayExitIntoRoundabout_liveMapJunctionView -> Motorway exit into roundabout · live map junction view"""
    parts = [re.sub(r"(?<!^)(?=[A-Z])", " ", p).replace("0102", "01.02").replace("0503", "05.03") for p in name.split("_")]
    text = " · ".join(p.strip().lower() for p in parts if p)
    return text[:1].upper() + text[1:]


def value(v):
    if isinstance(v, float):
        return f"{v:,.1f}"
    if isinstance(v, (list, dict)):
        return f"{len(v)} items"
    return "—" if v is None else str(v)


def build(doc, media_dir):
    media_dir = Path(media_dir)
    files = {p.name: p for p in media_dir.rglob("*.png")}
    tests = [t | {"item": item} for item, group in doc["checklist"].items() for t in group["tests"]]
    passed = sum(t["status"] == "passed" for t in tests)
    uk = datetime.now(timezone.utc).astimezone(ZoneInfo("Europe/London"))
    run_id = doc["run_url"].rstrip("/").rsplit("/", 1)[-1]
    out = []
    w = out.append
    w('<title>DriveMate run ' + html.escape(run_id) + '</title>')
    w('<meta name="claude:viewport-fit-sides" content="cover">')
    w('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&family=JetBrains+Mono:wght@400;600&display=swap">')
    w("""<style>
/* Layout: a run header, then one row per test: verdict, numbers, then a strip of the screens it saw. */
:root {
  --bg: #f4f6f8; --panel: #ffffff; --ink: #18202b; --muted: #5a6675; --rule: #d9dee5;
  --accent: #373e9e; --pass: #1d7a46; --pass-bg: #e3f3ea; --fail: #b3261e; --fail-bg: #fbe6e4;
  --body: "Atkinson Hyperlegible", "Segoe UI", system-ui, sans-serif;
  --data: "JetBrains Mono", ui-monospace, "SFMono-Regular", Menlo, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #11151b; --panel: #1a2029; --ink: #e8edf3; --muted: #9aa6b5; --rule: #2c3542;
  --accent: #9ea6ff; --pass: #5fd08f; --pass-bg: #163626; --fail: #ff8a80; --fail-bg: #3d1c1a; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #11151b; --panel: #1a2029; --ink: #e8edf3; --muted: #9aa6b5; --rule: #2c3542;
  --accent: #9ea6ff; --pass: #5fd08f; --pass-bg: #163626; --fail: #ff8a80; --fail-bg: #3d1c1a; color-scheme: dark }
body { background: var(--bg); color: var(--ink); font: 15px/1.5 var(--body);
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 24px 48px; }
main { max-width: 1100px; margin: 0 auto; display: grid; gap: 20px; }
header { display: grid; gap: 6px; }
h1 { font-size: 1.6rem; margin: 0; text-wrap: balance; }
.meta { color: var(--muted); font: 0.85rem/1.5 var(--data); overflow-wrap: anywhere; }
.meta a { color: var(--accent); }
.score { font: 700 1.1rem var(--body); }
.score b { font-variant-numeric: tabular-nums; }
.test { background: var(--panel); border: 1px solid var(--rule); border-radius: 8px; padding: 16px; display: grid; gap: 12px; min-width: 0; }
.test.failed { border-left: 4px solid var(--fail); }
.head { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: baseline; }
.pill { font: 700 0.75rem var(--data); letter-spacing: 0.06em; text-transform: uppercase; padding: 2px 8px; border-radius: 999px; }
.pill.passed { color: var(--pass); background: var(--pass-bg); }
.pill.failed, .pill.missing { color: var(--fail); background: var(--fail-bg); }
.item { font: 600 0.8rem var(--data); color: var(--accent); }
h2 { font-size: 1.05rem; margin: 0; min-width: 0; }
.why { margin: 0; color: var(--fail); font: 0.85rem/1.5 var(--data); overflow-wrap: anywhere; }
dl { margin: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 4px 16px; }
dl div { display: flex; justify-content: space-between; gap: 8px; border-bottom: 1px dotted var(--rule); min-width: 0; }
dt { color: var(--muted); font-size: 0.85rem; overflow-wrap: anywhere; }
dd { margin: 0; font: 600 0.85rem var(--data); font-variant-numeric: tabular-nums; text-align: right; overflow-wrap: anywhere; }
.shots { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 4px; }
figure { margin: 0; flex: 0 0 auto; width: 160px; display: grid; gap: 4px; }
figure img { width: 160px; border-radius: 4px; border: 1px solid var(--rule); }
figcaption { font: 0.75rem/1.3 var(--data); color: var(--muted); overflow-wrap: anywhere; }
</style>""")
    w("<main><header>")
    w(f"<h1>Emulator run {html.escape(run_id)}</h1>")
    w(f'<p class="score"><b>{passed}/{len(tests)}</b> tests passed · {"all passed" if passed == len(tests) else "not ready for an APK"}</p>')
    w(f'<p class="meta">App {html.escape(doc["app_commit"][:7])} · public tests {html.escape(doc["public_tests_sha"][:7])} · '
      f'page made {uk:%d %b %Y, %H:%M} UK · <a href="{html.escape(doc["run_url"])}">run on GitHub</a></p>')
    w("</header>")
    for t in sorted(tests, key=lambda t: (t["status"] == "passed", t["item"])):
        status = t["status"]
        w(f'<section class="test {html.escape(status)}"><div class="head">'
          f'<span class="pill {html.escape(status)}">{html.escape(status)}</span>'
          f'<span class="item">{html.escape(t["item"])}</span><h2>{html.escape(words(t["name"]))}</h2></div>')
        if t.get("failure"):
            w(f'<p class="why">{html.escape(str(t["failure"])[:600])}</p>')
        verdict = next((e for e in (t.get("timeline") or []) if e.get("event") == "verdict"), {})
        rows = [(k, v) for k, v in verdict.items() if k not in SKIP_KEYS and not isinstance(v, (list, dict))]
        if rows:
            w("<dl>" + "".join(f"<div><dt>{html.escape(k.replace('_', ' '))}</dt><dd>{html.escape(value(v))}</dd></div>" for k, v in rows) + "</dl>")
        shots = [m for m in t.get("media", []) if m.endswith(".png") and m in files and not m.endswith("-after-start.png")]
        if shots:
            w('<div class="shots">')
            for m in shots:
                src = thumb(files[m])
                if src:
                    w(f'<figure><img src="{src}" alt="{html.escape(m)}" loading="lazy">'
                      f'<figcaption>{html.escape(m.split("-", 1)[-1].removesuffix(".png"))}</figcaption></figure>')
            w("</div>")
        w("</section>")
    w("</main>")
    return "\n".join(out) + "\n"


def main():
    doc = json.loads(Path(sys.argv[1]).read_text())
    Path(sys.argv[3]).write_text(build(doc, sys.argv[2]))


if __name__ == "__main__":
    main()
