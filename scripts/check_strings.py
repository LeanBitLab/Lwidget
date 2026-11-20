#!/usr/bin/env python3
"""Verify string resources: locale consistency, unused keys, hardcoded UI text.

Checks:
  1. Per locale, against the base values/strings.xml:
     - missing keys (present in base, absent in locale)
     - extra keys (present in locale, absent in base)
     - format placeholder mismatch (%s, %d, %1$s, ...)
     - untranslated values (identical to English, see SAME_AS_BASE_OK)
  2. Unused keys: base strings never referenced as R.string.x / @string/x
     (or R.array / R.plurals) in code, layouts, xml or the manifest.
  3. Hardcoded UI text: English-looking string literals in Kotlin sources.
     These never reach translators, so the locale checks above cannot see them.
     Suppress a genuine non-UI literal with a trailing `// i18n-ignore`, or a
     block with `// i18n-ignore-start` ... `// i18n-ignore-end`.
Entries with translatable="false" in the base file are skipped by 1 and 3.

Usage: python3 scripts/check_strings.py [res_dir]
Exit code 1 if any problem is found.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res")
SRC = RES.parent  # app/src/main
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdfxXc%n]")
REF_TYPES = {"string": "string", "string-array": "array", "plurals": "plurals"}

# Keys whose value may equal English in some locales (cognates, brand and unit names).
# Add a key here only after checking the word really is used as-is in that language.
SAME_AS_BASE_KEYS = {
    "app_name", "category_agenda", "category_appearance", "category_system", "changelog_version",
    "font_cursive", "font_monospace", "font_serif", "preset_cockpit", "preset_minimal",
    "preset_monochrome", "preset_neon", "section_date", "temp_unit_celsius", "temp_unit_fahrenheit",
    "duration_hours_minutes", "duration_minutes", "section_ram", "widget_title_numbered",
    "widget_selector_numbered", "widget_option_numbered",
}

# --- hardcoded literal detection -------------------------------------------
STRING_LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')
# Lines whose literals are never shown to users.
NON_UI_LINE = re.compile(
    r"^\s*@[\w.:]+|Log\.[dviwe]\(|throw |Exception\(|const val|import |"
    r"\.(get|put)(String|Int|Boolean|Long|Float|StringSet)\(|getSharedPreferences|"
    r"ofPattern|getFormatter|setFormat(12|24)Hour|Intent\(|\.action|Uri\.parse|"
    r"getMethod|setTimeZone|contentResolver|\b(selection|sortOrder|projection)\b"
)
TEMPLATE = re.compile(r"\$\{[^}]*\}|\$[A-Za-z_]\w*")
IDENTIFIER = re.compile(r"^[a-z][A-Za-z0-9]*$|^[a-z0-9_.:/-]*$")  # camelCase, keys, paths
WORD = re.compile(r"[A-Za-z]{3,}|[A-Za-z]+ [A-Za-z]+")
DATE_PATTERN = re.compile(r"^[EMLdHhkKmsSayzZ ,:./'-]+$")  # DateTimeFormatter patterns
COLOR = re.compile(r"^#[0-9A-Fa-f]{6,8}$")
DOTTED_NAME = re.compile(r"^\w+(\.\w+)+$")  # package, permission and action names


def load(path):
    """Return {name: (tag, text)} for translatable <string>, <plurals>, <string-array>."""
    out = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if not name or el.get("translatable") == "false":
            continue
        out[name] = (el.tag, "".join(el.itertext()).strip())
    return out


def placeholders(text):
    return sorted(p for p in PLACEHOLDER.findall(text) if p != "%%")


def check_locales(base, problems):
    for loc_file in sorted(RES.glob("values-*/strings.xml")):
        loc = load(loc_file)
        name = loc_file.parent.name
        for key in sorted(base.keys() - loc.keys()):
            problems.append(f"{name}: missing '{key}'")
        for key in sorted(loc.keys() - base.keys()):
            problems.append(f"{name}: extra '{key}' (not in base)")
        for key in sorted(base.keys() & loc.keys()):
            b, l = base[key][1], loc[key][1]
            if placeholders(b) != placeholders(l):
                problems.append(
                    f"{name}: placeholder mismatch in '{key}': {placeholders(b)} vs {placeholders(l)}"
                )
            elif b == l and key not in SAME_AS_BASE_KEYS and re.search(r"[A-Za-z]{3,}", PLACEHOLDER.sub("", b)):
                problems.append(f"{name}: untranslated '{key}' (same as English: {b!r})")


def check_unused(problems):
    base_all = {el.get("name"): el.tag for el in ET.parse(RES / "values" / "strings.xml").getroot()}
    corpus = []
    for p in SRC.rglob("*"):
        if p.suffix in (".kt", ".java", ".xml") and not p.parent.name.startswith("values"):
            corpus.append(p.read_text(encoding="utf-8", errors="ignore"))
    corpus = "\n".join(corpus)
    for name, tag in base_all.items():
        t = REF_TYPES.get(tag, tag)
        if not re.search(rf"(R\.{t}\.|@{t}/){re.escape(name)}\b", corpus):
            problems.append(f"values: unused '{name}' (no R.{t}.{name} / @{t}/{name} reference)")


def check_hardcoded(problems):
    for f in sorted((SRC / "java").rglob("*.kt")):
        ignoring = False
        for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if "i18n-ignore-start" in line:
                ignoring = True
            if "i18n-ignore-end" in line:
                ignoring = False
                continue
            stripped = line.lstrip()
            code = "" if stripped.startswith(("//", "*", "/*")) else line.split("//")[0]
            if ignoring or "i18n-ignore" in line or not code.strip() or NON_UI_LINE.search(code):
                continue
            for m in STRING_LIT.finditer(code):
                text = TEMPLATE.sub("", m.group(1)).strip()
                if (not text or IDENTIFIER.match(text) or DATE_PATTERN.match(text)
                        or COLOR.match(text) or DOTTED_NAME.match(text) or not WORD.search(text)):
                    continue
                if re.fullmatch(r"[A-Z0-9_ ]+", text):  # SQL keywords, constants
                    continue
                rel = f.relative_to(SRC.parent.parent)
                problems.append(f"{rel}:{i}: hardcoded text \"{m.group(1)}\" (use a string resource)")


def main():
    base = load(RES / "values" / "strings.xml")
    problems = []
    check_locales(base, problems)
    check_unused(problems)
    check_hardcoded(problems)
    if problems:
        print("String resource problems:")
        for p in problems:
            print("  " + p)
        return 1
    print(f"OK: {len(base)} base strings consistent, used, and no hardcoded UI text")
    return 0


if __name__ == "__main__":
    sys.exit(main())
