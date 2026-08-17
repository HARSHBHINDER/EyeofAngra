#!/usr/bin/env python3
"""Render the version table in README.md from versions.json.

Run locally (`python scripts/render_versions.py`) or from CI — it rewrites the
block between the VERSIONS markers in README.md and is a no-op if nothing changed.
Each row gets two download buttons: the in-repo APK under APKs/, and the GitHub
Release asset (when the entry has a release_tag).
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = json.loads((ROOT / "versions.json").read_text(encoding="utf-8"))
README = ROOT / "README.md"

START = "<!-- VERSIONS:TABLE:START -->"
END = "<!-- VERSIONS:TABLE:END -->"

REPO = DATA["repo"]
ASSET = DATA["release_asset"]


def badge(label, color, logo):
    label = label.replace("-", "--").replace(" ", "%20")
    return f"https://img.shields.io/badge/{label}-{color}?style=flat-square&logo={logo}&logoColor=white"


def download_cell(entry):
    apk = entry["apk"]
    repo_url = f"https://github.com/{REPO}/raw/main/APKs/{apk}"
    buttons = [
        f'<a href="{repo_url}"><img src="{badge("APK in-repo", "2EA44F", "android")}" alt="Download {apk} from repo"></a>'
    ]
    tag = entry.get("release_tag")
    if tag:
        rel_url = f"https://github.com/{REPO}/releases/download/{tag}/{ASSET}"
        buttons.append(
            f'<a href="{rel_url}"><img src="{badge("APK release", "24292E", "github")}" alt="Download from GitHub release"></a>'
        )
    return "<br>".join(buttons)


def render():
    rows = []
    for i, entry in enumerate(DATA["versions"], start=1):
        changes = "".join(f"<li>{c}</li>" for c in entry["changes"])
        rows.append(
            "<tr>"
            f'<td align="center">{i}</td>'
            f'<td align="center"><strong>v{entry["version"]}</strong><br><sub>{entry["date"]}</sub></td>'
            f"<td><ul>{changes}</ul></td>"
            f'<td align="center">{download_cell(entry)}</td>'
            "</tr>"
        )
    return (
        "<table>\n"
        "<thead><tr>"
        "<th>#</th><th>Version</th><th>Changes &amp; features</th><th>Download</th>"
        "</tr></thead>\n"
        "<tbody>\n" + "\n".join(rows) + "\n</tbody>\n</table>"
    )


def main():
    text = README.read_text(encoding="utf-8")
    if START not in text or END not in text:
        sys.exit(f"README.md is missing the {START} / {END} markers.")
    block = f"{START}\n{render()}\n{END}"
    new = re.sub(re.escape(START) + r".*?" + re.escape(END), block, text, flags=re.DOTALL)
    if new != text:
        README.write_text(new, encoding="utf-8")
        print("README version table updated.")
    else:
        print("README version table already current.")


if __name__ == "__main__":
    main()
