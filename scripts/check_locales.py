"""Validate Simplified Chinese coverage and Android/Nav format contracts (stdlib only)."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / "app/src/main/res"


def strings(path):
    nodes = ET.parse(path).getroot().findall("string")
    names = [node.attrib["name"] for node in nodes]
    assert len(names) == len(set(names)), f"Duplicate keys: {path}"
    return {node.attrib["name"]: "".join(node.itertext()) for node in nodes
            if node.attrib.get("translatable") != "false"}


def formats(value):
    tokens = re.findall(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])", value)
    args = Counter()
    implicit = 0
    for index, kind in tokens:
        if kind in ("%", "n"):
            continue
        if not index:
            implicit += 1
        args[(int(index) if index else implicit, kind)] += 1
    return args


default = strings(res / "values/strings.xml")
chinese = strings(res / "values-zh-rCN/strings.xml")
assert default.keys() == chinese.keys(), (
    f"Missing: {sorted(default.keys() - chinese.keys())}; "
    f"extra: {sorted(chinese.keys() - default.keys())}"
)
for key, value in default.items():
    assert chinese[key].strip(), f"Empty translation: {key}"
    assert formats(value) == formats(chinese[key]), f"Format mismatch: {key}"
    assert Counter(re.findall(r"\{[a-zA-Z_]+\}", value)) == Counter(
        re.findall(r"\{[a-zA-Z_]+\}", chinese[key])
    ), f"Navigation argument mismatch: {key}"
for path in res.rglob("*.xml"):
    ET.parse(path)
print(f"PASS: {len(default)} strings, format/navigation parity, all resource XML parses")
