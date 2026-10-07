#!/usr/bin/env python3
"""Static checks for the compact ColorOS dashboard layout."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/res/values/dimens.xml"
LARGE = ROOT / "app/src/main/res/values-w420dp/dimens.xml"
LAYOUT = ROOT / "app/src/main/res/layout/dashboard_scroll_content.xml"

ANDROID = "{http://schemas.android.com/apk/res/android}"


def read_dimens(path: Path):
    root = ET.parse(path).getroot()
    out = {}
    for item in root.findall("dimen"):
        raw = (item.text or "").strip()
        if raw.endswith("dp"):
            out[item.attrib["name"]] = float(raw[:-2])
    return out


base = read_dimens(BASE)
large = {**base, **read_dimens(LARGE)}

profiles = [
    ("narrow-320dp", 320),
    ("compact-360dp", 360),
    ("findx6pro-class-393dp", 393),
    ("compact-411dp", 411),
    ("large-430dp", 430),
    ("ultra-class-480dp", 480),
]

for name, width in profiles:
    d = large if width >= 420 else base
    page = d["page_horizontal_padding"]
    content = width - 2 * page
    hero_inner = content - 2 * d["hero_inner_padding"]
    title_space = (
        hero_inner
        - d["language_button_width"]
        - d["language_button_margin_start"]
    )
    metric_tile = (content - 2 * 16 - 10) / 2

    assert content >= 292, f"{name}: content too narrow ({content}dp)"
    assert title_space >= 135, f"{name}: header title area too tight ({title_space}dp)"
    assert metric_tile >= 120, f"{name}: metric tile too narrow ({metric_tile}dp)"
    assert d["card_gap"] >= 10, f"{name}: card spacing is too dense"

layout = ET.parse(LAYOUT).getroot()
ids = {
    node.attrib.get(ANDROID + "id")
    for node in layout.iter()
    if node.attrib.get(ANDROID + "id")
}

required = {
    "@+id/contentRoot",
    "@+id/statusHeadline",
    "@+id/statusBadge",
    "@+id/statusText",
    "@+id/gmsValueText",
    "@+id/dozeValueText",
    "@+id/watchdogValueText",
    "@+id/fcmValueText",
    "@+id/lastReconnectTimeText",
    "@+id/lastReconnectReasonText",
    "@+id/reconnectCountText",
    "@+id/wakeBtn",
    "@+id/diagBtn",
    "@+id/protectionSwitch",
    "@+id/notificationSwitch",
    "@+id/openAutostartBtn",
    "@+id/openAssociateBtn",
    "@+id/openBatteryBtn",
    "@+id/scanFcmAppsBtn",
    "@+id/fcmAppsContainer",
    "@+id/appearanceGroup",
}

missing = sorted(required - ids)
assert not missing, f"missing dashboard ids: {missing}"

root_top = layout.attrib.get(ANDROID + "paddingTop", "")
assert root_top == "@dimen/page_top_padding", (
    "dashboard must use responsive page_top_padding instead of fixed overlap padding"
)

print("ColorOS dashboard checks passed for:")
for name, width in profiles:
    print(f"  - {name}: {width}dp")
