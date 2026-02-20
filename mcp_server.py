# /// script
# requires-python = ">=3.10"
# dependencies = ["mcp>=1.0"]
# ///
"""
Account Ledger Service - MCP 서버

Claude Code에 프로젝트 전용 도구를 추가합니다.
.mcp.json에 등록되어 이 프로젝트 열 때 자동으로 활성화됩니다.

사용 가능한 도구:
  ledger_build          ./gradlew clean build
  ledger_test           ./gradlew test [--tests filter]
  ledger_coverage       ./gradlew koverHtmlReport koverLog
  ledger_create_issue   gh issue create
  ledger_create_branch  git checkout -b feature/issue-{n}-{desc}
  ledger_create_pr      gh pr create (Closes #n 자동 포함)
"""
from __future__ import annotations

import json
import os
import re
import subprocess
from pathlib import Path

from mcp.server.fastmcp import FastMCP

PROJECT_DIR = Path(os.getenv("LEDGER_PROJECT_DIR", Path(__file__).parent))
mcp = FastMCP("ledger")


# ── Gradle ──────────────────────────────────────────────────────────────────

@mcp.tool()
def ledger_build(clean: bool = True) -> str:
    """Gradle로 프로젝트를 빌드합니다. (./gradlew clean build)"""
    tasks = ["clean", "build"] if clean else ["build"]
    result = subprocess.run(
        [str(PROJECT_DIR / "gradlew")] + tasks,
        cwd=PROJECT_DIR, capture_output=True, text=True, timeout=300,
    )
    return json.dumps({
        "success": result.returncode == 0,
        "stdout": result.stdout[-5000:],
        "stderr": result.stderr[-2000:],
    }, ensure_ascii=False)


@mcp.tool()
def ledger_test(test_filter: str = "") -> str:
    """테스트를 실행합니다. test_filter 예: 'TransferServiceTest' (빈 문자열이면 전체)"""
    cmd = [str(PROJECT_DIR / "gradlew"), "test", "--rerun-tasks"]
    if test_filter:
        cmd += ["--tests", test_filter]
    result = subprocess.run(
        cmd, cwd=PROJECT_DIR, capture_output=True, text=True, timeout=300,
    )
    return json.dumps({
        "success": result.returncode == 0,
        "stdout": result.stdout[-8000:],
        "stderr": result.stderr[-2000:],
    }, ensure_ascii=False)


@mcp.tool()
def ledger_coverage() -> str:
    """Kover 커버리지 리포트를 생성하고 결과를 반환합니다. (./gradlew koverHtmlReport koverLog)"""
    result = subprocess.run(
        [str(PROJECT_DIR / "gradlew"), "koverHtmlReport", "koverLog"],
        cwd=PROJECT_DIR, capture_output=True, text=True, timeout=300,
    )
    # 커버리지 수치 추출
    coverage_line = next(
        (l.strip() for l in result.stdout.splitlines() if "%" in l), ""
    )
    return json.dumps({
        "success": result.returncode == 0,
        "coverage_summary": coverage_line,
        "report": str(PROJECT_DIR / "build/reports/kover/html/index.html"),
        "stdout": result.stdout[-3000:],
    }, ensure_ascii=False)


# ── GitHub ───────────────────────────────────────────────────────────────────

@mcp.tool()
def ledger_create_issue(title: str, body: str, labels: list[str] | None = None) -> str:
    """GitHub Issue를 생성합니다. (모든 코드 변경 전 먼저 호출)"""
    cmd = ["gh", "issue", "create", "--title", title, "--body", body]
    if labels:
        cmd += ["--label", ",".join(labels)]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    url = result.stdout.strip()
    match = re.search(r"/issues/(\d+)", url)
    return json.dumps({
        "success": result.returncode == 0,
        "issue_number": int(match.group(1)) if match else 0,
        "url": url,
        "error": result.stderr.strip(),
    }, ensure_ascii=False)


@mcp.tool()
def ledger_create_branch(issue_number: int, description: str) -> str:
    """feature/issue-{번호}-{설명} 브랜치를 생성합니다."""
    slug = re.sub(r"[^a-zA-Z0-9]", "-", description.lower())
    slug = re.sub(r"-+", "-", slug).strip("-")[:50]
    branch = f"feature/issue-{issue_number}-{slug}"
    result = subprocess.run(
        ["git", "checkout", "-b", branch],
        cwd=PROJECT_DIR, capture_output=True, text=True, timeout=30,
    )
    return json.dumps({
        "success": result.returncode == 0,
        "branch_name": branch,
        "error": result.stderr.strip(),
    }, ensure_ascii=False)


@mcp.tool()
def ledger_create_pr(title: str, body: str, issue_number: int, base: str = "main") -> str:
    """PR을 생성합니다. 본문에 'Closes #이슈번호'가 자동으로 추가됩니다."""
    full_body = f"{body}\n\nCloses #{issue_number}"
    result = subprocess.run(
        ["gh", "pr", "create", "--title", title, "--body", full_body, "--base", base],
        cwd=PROJECT_DIR, capture_output=True, text=True, timeout=60,
    )
    url = result.stdout.strip()
    match = re.search(r"/pull/(\d+)", url)
    return json.dumps({
        "success": result.returncode == 0,
        "pr_number": int(match.group(1)) if match else 0,
        "url": url,
        "error": result.stderr.strip(),
    }, ensure_ascii=False)


if __name__ == "__main__":
    mcp.run(transport="stdio")
