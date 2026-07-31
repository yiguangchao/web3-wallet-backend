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
    parser.add_argument(
        "--required-only",
        action="store_true",
        help="Only fail for missing, skipped, or failed tests named by --require.",
    )
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
    successful: set[str] = set()
    failed_cases: dict[str, str] = {}
    skipped_cases: set[str] = set()
    for report_file in report_files:
        root = ET.parse(report_file).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
        for suite in suites:
            for key in totals:
                totals[key] += int(suite.attrib.get(key, "0"))
            for case in suite.findall("testcase"):
                class_name = case.attrib.get("classname", "")
                method_name = case.attrib.get("name", "")
                selector = f"{class_name}#{method_name}"
                skipped_node = case.find("skipped")
                failure_node = case.find("failure")
                error_node = case.find("error")
                if skipped_node is not None:
                    skipped_cases.add(selector)
                    continue
                executed.add(selector)
                problem = failure_node if failure_node is not None else error_node
                if problem is None:
                    successful.add(selector)
                else:
                    message = problem.attrib.get("message", "").strip()
                    failed_cases[selector] = message or "test failed without a message"

    failed = totals["failures"] + totals["errors"]
    passed = totals["tests"] - failed - totals["skipped"]
    lines = [
        f"Tests run: {totals['tests']}",
        f"Tests passed: {passed}",
        f"Tests failed: {failed}",
        f"Tests skipped: {totals['skipped']}",
    ]
    print("\n".join(lines))

    if failed_cases:
        print("Failed tests:", file=sys.stderr)
        for selector, message in sorted(failed_cases.items()):
            compact_message = " ".join(message.split())
            print(f"- {selector}: {compact_message[:500]}", file=sys.stderr)

    if args.write_summary and os.getenv("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
            summary.write("## Test results\n\n")
            summary.write("\n".join(f"- {line}" for line in lines))
            summary.write("\n")

    required = set(args.require)
    observed = executed | skipped_cases
    missing = sorted(required - observed)
    required_failed = sorted(required & set(failed_cases))
    required_skipped = sorted(required & skipped_cases)
    if missing:
        print("Required tests were not reported:", file=sys.stderr)
        for selector in missing:
            print(f"- {selector}", file=sys.stderr)
    if required_failed:
        print("Required tests failed:", file=sys.stderr)
        for selector in required_failed:
            print(f"- {selector}", file=sys.stderr)
    if required_skipped:
        print("Required tests were skipped:", file=sys.stderr)
        for selector in required_skipped:
            print(f"- {selector}", file=sys.stderr)

    if missing or required_failed or required_skipped:
        return 1
    if failed > 0 and not args.required_only:
        return 1
    if args.fail_on_skipped and totals["skipped"] > 0:
        print("CI forbids skipped tests; Docker-backed tests may not be skipped.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
