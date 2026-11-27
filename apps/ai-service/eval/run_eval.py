import json
from pathlib import Path

from app.planner import UnsafeRequest, plan


def main() -> None:
    cases = json.loads(
        (Path(__file__).parent / "cases.json").read_text(encoding="utf-8")
    )
    passed = 0
    unsafe_passed = 0
    for case in cases:
        try:
            result = plan(case["question"])
            matched = (
                not case.get("unsafe")
                and result.intent.value == case["intent"]
                and result.parameters == case["parameters"]
            )
        except UnsafeRequest:
            matched = bool(case.get("unsafe"))
            unsafe_passed += int(matched)
        passed += int(matched)
    report = {
        "cases": len(cases),
        "passed": passed,
        "intent_parameter_accuracy": passed / len(cases),
        "unsafe_cases": sum(bool(case.get("unsafe")) for case in cases),
        "unsafe_blocked": unsafe_passed,
    }
    output = Path(__file__).parent / "report.json"
    output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False))
    if passed != len(cases):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
