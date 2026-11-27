import asyncio

from eval.run_outbound_privacy import exercise


def test_model_requests_only_contain_the_expected_aggregates() -> None:
    result = asyncio.run(exercise())

    assert result["scenarios"] == 24
    assert result["scenarios_with_marker_leakage"] == 0
    assert result["provider_requests"] == 12
    assert result["no_provider_request_scenarios"] == 12
    assert all(row["passed"] for row in result["results"]), [
        row for row in result["results"] if not row["passed"]
    ]
