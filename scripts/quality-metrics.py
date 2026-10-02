#!/usr/bin/env python3
"""Extrai métricas de qualidade dos relatórios de build para os badges do README.

Uso: quality-metrics.py <coverage|mutation|tests-unit|tests-integration>

Imprime linhas `chave=valor` pensadas para `$GITHUB_OUTPUT`:
`value` (texto do badge) e `color` (cor do badge).
"""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET


def color_for(percent: float) -> str:
    if percent >= 90:
        return "brightgreen"
    if percent >= 75:
        return "yellow"
    return "red"


def coverage() -> dict[str, str]:
    root = ET.parse("build/reports/jacoco/test/jacocoTestReport.xml").getroot()
    counter = next(c for c in root.findall("counter") if c.get("type") == "INSTRUCTION")
    covered, missed = int(counter.get("covered")), int(counter.get("missed"))
    percent = covered / (covered + missed) * 100
    return {"value": f"{percent:.1f}%", "color": color_for(percent)}


def mutation() -> dict[str, str]:
    mutations = ET.parse("build/reports/pitest/mutations.xml").getroot().findall("mutation")
    killed = sum(1 for m in mutations if m.get("status") == "KILLED")
    total = len(mutations)
    percent = killed / total * 100 if total else 100.0
    return {"value": f"{killed}/{total}", "color": color_for(percent)}


def tests(source_set: str) -> dict[str, str]:
    total = 0
    for path in glob.glob(f"build/test-results/{source_set}/*.xml"):
        total += int(ET.parse(path).getroot().get("tests", 0))
    return {"value": f"{total} passed", "color": "brightgreen"}


METRICS = {
    "coverage": coverage,
    "mutation": mutation,
    "tests-unit": lambda: tests("test"),
    "tests-integration": lambda: tests("integrationTest"),
}


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in METRICS:
        print(f"usage: {sys.argv[0]} <{'|'.join(METRICS)}>", file=sys.stderr)
        sys.exit(2)
    for key, value in METRICS[sys.argv[1]]().items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
