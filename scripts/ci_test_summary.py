#!/usr/bin/env python3
"""Summarize Surefire XML and enforce the CI integration-test contract."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--require", action="append", default=[])
    parser.add_argument("--fail-on-skipped", action="store_true")
    parser.add_argument("--write-summary", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report_files = sorted(args.reports.glob("TEST-*.xml"))
    if not report_files:
        print(f"No Surefire XML reports found in {args.reports}", file=sys.stderr)
        return 1

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    executed: set[str] = set()
    for report_file in report_files:
        root = ET.parse(report_file).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
        for suite in suites:
            for key in totals:
                totals[key] += int(suite.attrib.get(key, "0"))
            for case in suite.findall("testcase"):
                class_name = case.attrib.get("classname", "")
                method_name = case.attrib.get("name", "")
                if case.find("skipped") is None:
                    executed.add(f"{class_name}#{method_name}")

    failed = totals["failures"] + totals["errors"]
    passed = totals["tests"] - failed - totals["skipped"]
    lines = [
        f"Tests run: {totals['tests']}",
        f"Tests passed: {passed}",
        f"Tests failed: {failed}",
        f"Tests skipped: {totals['skipped']}",
    ]
    print("\n".join(lines))

    if args.write_summary and os.getenv("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
            summary.write("## Test results\n\n")
            summary.write("\n".join(f"- {line}" for line in lines))
            summary.write("\n")

    missing = sorted(set(args.require) - executed)
    if missing:
        print("Required tests did not execute:", file=sys.stderr)
        for selector in missing:
            print(f"- {selector}", file=sys.stderr)

    if failed > 0 or missing:
        return 1
    if args.fail_on_skipped and totals["skipped"] > 0:
        print("CI forbids skipped tests; Docker-backed tests may not be skipped.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

