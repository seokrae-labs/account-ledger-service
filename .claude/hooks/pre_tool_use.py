#!/usr/bin/env python3
from __future__ import annotations
"""
PreToolUse 훅 - 컨벤션 위반 자동 차단 (외부 의존성 없음)

Claude Code가 Write/Edit/Bash 전에 자동으로 실행합니다.
stdin으로 JSON을 받고, stdout으로 결과를 반환합니다.

차단 규칙:
  [Kotlin 파일]
  - application/service/ 에 @Service, @Component 사용
  - 어디서든 @Transactional 사용 (→ TransactionalOperator.executeAndAwait)
  - domain/model/ 에 suspend fun 사용
  - domain/ 에 Mono<, Flux< 사용
  - adapter/out/persistence/ 에 Dispatchers.IO 사용

  [Bash]
  - rm -rf, git push --force, git reset --hard, sudo
"""
import json
import re
import sys

# ── 컨벤션 규칙 ──────────────────────────────────────────────────────────────
# (경로 패턴, 금지 코드 패턴, 메시지, 권장 대안)
CONVENTIONS = [
    (
        r"application[/\\]service[/\\]",
        r"@(Service|Component)\b",
        "@Service/@Component는 application/service/ 금지",
        "@Bean + @Configuration 사용",
    ),
    (
        r"\.kt$",
        r"@Transactional\b",
        "@Transactional 금지",
        "transactionExecutor.execute { } 사용 (CLAUDE.md 참조)",
    ),
    (
        r"domain[/\\]model[/\\]",
        r"\bsuspend\s+fun\b",
        "domain/model/ 에 suspend fun 금지",
        "도메인 모델은 순수 함수 — I/O는 port/adapter 레이어에서",
    ),
    (
        r"domain[/\\]",
        r"\b(Mono|Flux)<",
        "domain/ 에 Mono/Flux 금지",
        "suspend fun 반환 타입 사용 (100% Coroutine-Native)",
    ),
    (
        r"adapter[/\\]out[/\\]persistence[/\\]",
        r"Dispatchers\.IO\b",
        "adapter/out/persistence/ 에 Dispatchers.IO 금지",
        "R2DBC는 이미 non-blocking — withContext(Dispatchers.IO) 불필요",
    ),
]

# ── 위험 Bash 패턴 ────────────────────────────────────────────────────────────
DANGEROUS_BASH = [
    (r"\brm\s+-rf\b",               "rm -rf 금지"),
    (r"\bgit\s+push\s+.*--force\b", "git push --force 금지"),
    (r"\bgit\s+reset\s+--hard\b",   "git reset --hard 금지"),
    (r"\bsudo\b",                   "sudo 금지"),
]


def check_conventions(file_path: str, content: str) -> str | None:
    """위반 발견 시 이유 문자열, 통과 시 None"""
    for path_pat, code_pat, msg, suggestion in CONVENTIONS:
        if re.search(path_pat, file_path, re.IGNORECASE):
            if re.search(code_pat, content):
                return f"[컨벤션 위반] {msg}\n권장: {suggestion}"
    return None


def check_bash(command: str) -> str | None:
    for pat, msg in DANGEROUS_BASH:
        if re.search(pat, command):
            return f"[안전 규칙] {msg}"
    return None


def main() -> None:
    try:
        data = json.loads(sys.stdin.read())
    except (json.JSONDecodeError, ValueError):
        print(json.dumps({}))
        return

    tool = data.get("tool_name", "")
    inp  = data.get("tool_input", {})

    reason = None

    if tool == "Bash":
        reason = check_bash(inp.get("command", ""))

    elif tool in ("Write", "Edit"):
        path    = inp.get("file_path", "")
        content = inp.get("content") or inp.get("new_string") or ""
        if path.endswith(".kt") and content:
            reason = check_conventions(path, content)

    if reason:
        print(json.dumps({"decision": "block", "reason": reason}))
    else:
        print(json.dumps({}))


if __name__ == "__main__":
    main()
