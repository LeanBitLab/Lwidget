#!/usr/bin/env python3
"""Verify every localized strings.xml matches the base values/strings.xml.

Checks, per locale:
  - missing keys (present in base, absent in locale)
  - extra keys (present in locale, absent in base)
  - format placeholder mismatch (%s, %d, %1$s, ...)
Entries with translatable="false" in the base file are ignored.

Usage: python3 scripts/check_strings.py [res_dir]
Exit code 1 if any problem is found.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res")
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdfxXc%n]")


def load(path):
    """Return {name: text} for <string>, <plurals>, <string-array>; skip non-translatable."""
    out = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if not name or el.get("translatable") == "false":
            continue
        out[name] = "".join(el.itertext())
    return out


def placeholders(text):
    return sorted(p for p in PLACEHOLDER.findall(text) if p != "%%")


def main():
    base_file = RES / "values" / "strings.xml"
    base = load(base_file)
    problems = []
    for loc_file in sorted(RES.glob("values-*/strings.xml")):
        loc = load(loc_file)
        name = loc_file.parent.name
        for key in sorted(base.keys() - loc.keys()):
            problems.append(f"{name}: missing '{key}'")
        for key in sorted(loc.keys() - base.keys()):
            problems.append(f"{name}: extra '{key}' (not in base)")
        for key in sorted(base.keys() & loc.keys()):
            if placeholders(base[key]) != placeholders(loc[key]):
                problems.append(
                    f"{name}: placeholder mismatch in '{key}': "
                    f"{placeholders(base[key])} vs {placeholders(loc[key])}"
                )
    if problems:
        print("String resource problems:")
        for p in problems:
            print("  " + p)
        return 1
    print(f"OK: {len(base)} base strings consistent across all locales")
    return 0


if __name__ == "__main__":
    sys.exit(main())
