#!/usr/bin/env python3
"""Fail the build if a home-screen widget layout uses a view class that
RemoteViews refuses to inflate.

Android only lets widgets use a short list of views. Anything else (a plain
<View>, <Space>, <ScrollView>, AndroidX or custom views) makes the widget show
"Problem loading widget" and breaks the in-app preview with
"Class not allowed to be inflated".

Layouts checked: every res/layout/widget_*.xml, every layout named by
previewLayout / initialLayout in res/xml, and every R.layout.* used on a line
that mentions RemoteViews.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "app" / "src" / "main"
ALLOWED = {
    "AbsoluteLayout", "AdapterViewFlipper", "AnalogClock", "Button", "Chronometer",
    "FrameLayout", "GridLayout", "GridView", "ImageButton", "ImageView",
    "LinearLayout", "ListView", "ProgressBar", "RelativeLayout", "StackView",
    "TextClock", "TextView", "ViewFlipper", "ViewStub",
}
ANDROID = "{http://schemas.android.com/apk/res/android}"

names = {p.stem for p in (ROOT / "res/layout").glob("widget_*.xml")}
for xml in (ROOT / "res/xml").glob("*.xml"):
    text = xml.read_text(encoding="utf-8")
    names |= set(re.findall(r'(?:previewLayout|initialLayout)="@layout/(\w+)"', text))
for kt in (ROOT / "java").rglob("*.kt"):
    for line in kt.read_text(encoding="utf-8").splitlines():
        if "RemoteViews" in line:
            names |= set(re.findall(r"R\.layout\.(\w+)", line))

problems = []
for name in sorted(names):
    path = ROOT / "res/layout" / f"{name}.xml"
    if not path.exists():
        continue
    for node in ET.parse(path).getroot().iter():
        tag = node.tag
        if tag in ALLOWED or tag in ("include", "merge"):
            continue
        problems.append(f"{path.relative_to(ROOT.parent.parent.parent)}: <{tag}> is not allowed in a widget layout")

if problems:
    print("Widget layouts use views that RemoteViews cannot inflate:\n")
    print("\n".join(problems))
    print("\nUse FrameLayout instead of View/Space for spacers and bars, and see ALLOWED in this script.")
    sys.exit(1)
print(f"OK: {len(names)} widget layouts use only allowed views")
