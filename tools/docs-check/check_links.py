"""공개 마크다운의 상대 링크와 앵커를 확인한다.

대상은 저장소 루트의 *.md 와 docs/ · .github/ 아래의 *.md 다. 코드 블록 · 인라인 코드 밖의
[글자](대상) 링크마다 대상 경로가 있는지, #앵커가 있고 대상이 마크다운이면 그 파일의 제목 앵커에
있는지 본다. 스킴이 있는 링크(https: · mailto: …)는 보지 않는다. 깨진 링크가 하나라도 있으면
종료 코드 1 이다.

실행은 검사 컨테이너에서 한다(compose.check.yaml 의 docs — CONTRIBUTING.md §1). 이미지에 들어가는
파일은 Dockerfile.dockerignore 의 허용 목록이 정한다.
"""

import html
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote

ALLOWLIST = "tools/docs-check/Dockerfile.dockerignore"
# 이 파일이 대상에 없으면 허용 목록이 문서를 빠뜨린 이미지다 — 링크 0개로 통과하지 않게 한다.
REQUIRED = "docs/README.md"
SCANNED_DIRS = ("docs", ".github")

FENCE = re.compile(r"^[ \t]*(`{3,}|~{3,})(.*)$")
ATX_HEADING = re.compile(r"^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$")
INLINE_CODE = re.compile(r"(`+)(.+?)(?<!`)\1(?!`)")
# [글자](대상 "제목") — 대상은 <…> 이거나, 공백 없이 괄호 한 겹까지 품는다.
LINK = re.compile(
    r"\]\(\s*(<[^>\n]*>|(?:[^\s()]|\([^\s()]*\))*)"
    r"(?:\s+(?:\"[^\"]*\"|'[^']*'|\([^)]*\)))?\s*\)"
)
HEADING_LINK = re.compile(r"!?\[([^\]]*)\]\([^)]*\)")
HTML_TAG = re.compile(r"<[^>]+>")
BACKSLASH_ESCAPE = re.compile(r"\\([!-/:-@\[-`{-~])")
UNDERSCORE_EMPHASIS = re.compile(r"(?<!\w)(_{1,2})(?=\S)(.+?)(?<=\S)\1(?!\w)")
SCHEME = re.compile(r"^(?:[A-Za-z][A-Za-z0-9+.-]*:|//)")
# 지원하지 않는 링크 형식 — 건너뛰면 그 링크가 조용히 검사 밖에 남는다.
REFERENCE_DEFINITION = re.compile(r"^ {0,3}\[[^\]]+\]:\s*\S")
HTML_HREF = re.compile(r"<a\s[^>]*\bhref\s*=", re.IGNORECASE)


@dataclass(frozen=True)
class Problem:
    path: str
    line: int | None
    target: str
    reason: str

    def __str__(self) -> str:
        if self.line is None:
            return f"{self.path}: {self.reason}"
        return f"{self.path}:{self.line}: {self.target} — {self.reason}"


@dataclass
class Stats:
    files: int = 0
    links: int = 0
    relative: int = 0
    anchored: int = 0


def code_mask(lines: list[str]) -> list[bool]:
    """줄마다 펜스 코드 블록 안(펜스 줄 포함)이면 True."""
    mask = []
    fence = None
    for line in lines:
        m = FENCE.match(line)
        if fence is None:
            # 백틱 펜스의 정보 문자열에는 백틱이 없다 — 있으면 펜스가 아니라 인라인 코드다.
            if m and not (m.group(1)[0] == "`" and "`" in m.group(2)):
                fence = m.group(1)
                mask.append(True)
            else:
                mask.append(False)
            continue
        mask.append(True)
        if m and m.group(1)[0] == fence[0] and len(m.group(1)) >= len(fence) and not m.group(2).strip():
            fence = None
    return mask


def heading_text(raw: str) -> str:
    """제목 줄의 마크다운을 GitHub 이 그리는 글자로 — 인라인 코드는 내용을 그대로 두고, 그 밖에서
    링크는 글자만 남기고 이스케이프 · 엔티티 · 밑줄 강조 · HTML 태그를 푼다."""
    parts = []
    last = 0
    for m in INLINE_CODE.finditer(raw):
        parts.append(_plain(raw[last : m.start()]))
        parts.append(m.group(2))
        last = m.end()
    parts.append(_plain(raw[last:]))
    return "".join(parts).strip()


def _plain(text: str) -> str:
    text = HEADING_LINK.sub(r"\1", text)
    text = HTML_TAG.sub("", text)
    text = BACKSLASH_ESCAPE.sub(r"\1", text)
    text = UNDERSCORE_EMPHASIS.sub(r"\2", text)
    return html.unescape(text)


def slug(text: str) -> str:
    """GitHub 의 제목 앵커 — 소문자로, 글자(L) · 결합 부호(M) · 십진 숫자(Nd) · 연결 구두점(Pc) ·
    공백 · `-` 만 남기고, 공백을 `-` 로."""
    kept = []
    for c in text.lower():
        category = unicodedata.category(c)
        if c in " -" or category[0] in "LM" or category in ("Nd", "Pc"):
            kept.append(c)
    return "".join(kept).replace(" ", "-")


def heading_anchors(text: str) -> list[str]:
    """파일의 제목 앵커를 나오는 순서대로. 같은 앵커가 다시 나오면 `-1` · `-2` 를 붙인다."""
    lines = text.splitlines()
    seen: dict[str, int] = {}
    anchors = []
    for line, in_code in zip(lines, code_mask(lines)):
        m = None if in_code else ATX_HEADING.match(line)
        if not m:
            continue
        base = slug(heading_text(m.group(2) or ""))
        anchor = base
        while anchor in seen:
            seen[base] += 1
            anchor = f"{base}-{seen[base]}"
        seen[anchor] = 0
        anchors.append(anchor)
    return anchors


def extract_links(text: str) -> tuple[list[tuple[int, str]], list[tuple[int, str]]]:
    """코드 밖의 링크 (줄, 대상) 목록과 지원하지 않는 링크 형식 (줄, 원문) 목록."""
    lines = text.splitlines()
    links = []
    unsupported = []
    for number, (line, in_code) in enumerate(zip(lines, code_mask(lines)), start=1):
        if in_code:
            continue
        line = INLINE_CODE.sub("", line)
        if REFERENCE_DEFINITION.match(line) or HTML_HREF.search(line):
            unsupported.append((number, line.strip()))
        for m in LINK.finditer(line):
            target = m.group(1)
            if target.startswith("<"):
                target = target[1:-1]
            links.append((number, target))
    return links, unsupported


def markdown_files(root: Path) -> list[Path]:
    files = sorted(p for p in root.glob("*.md") if p.is_file())
    for directory in SCANNED_DIRS:
        files += sorted(p for p in (root / directory).rglob("*.md") if p.is_file())
    return files


def check(root: Path) -> tuple[list[Problem], Stats]:
    root = root.resolve()
    files = markdown_files(root)
    stats = Stats(files=len(files))
    problems = []
    if not files:
        problems.append(Problem(".", None, "", f"검사할 마크다운이 없다 — 허용 목록({ALLOWLIST})을 확인한다"))
    elif root / REQUIRED not in files:
        problems.append(Problem(REQUIRED, None, "", f"검사 대상에 없다 — 허용 목록({ALLOWLIST})을 확인한다"))

    anchors: dict[Path, set[str]] = {}

    def anchors_of(path: Path) -> set[str]:
        if path not in anchors:
            anchors[path] = set(heading_anchors(path.read_text(encoding="utf-8")))
        return anchors[path]

    for file in files:
        rel = file.relative_to(root).as_posix()
        links, unsupported = extract_links(file.read_text(encoding="utf-8"))
        for number, raw in unsupported:
            problems.append(Problem(rel, number, raw, "지원하지 않는 링크 형식"))
        for number, target in links:
            stats.links += 1
            if SCHEME.match(target):
                continue
            stats.relative += 1
            reason = _judge(root, file, target, stats, anchors_of)
            if reason:
                problems.append(Problem(rel, number, target, reason))
    return problems, stats


def _judge(root: Path, file: Path, target: str, stats: Stats, anchors_of) -> str | None:
    if target.startswith("/"):
        return "지원하지 않는 링크 형식 — 저장소 루트 기준 경로"
    path_part, _, anchor = target.partition("#")
    path_part = unquote(path_part.split("?", 1)[0])
    anchor = unquote(anchor)
    if anchor:
        stats.anchored += 1
    dest = (file.parent / path_part).resolve() if path_part else file
    if not dest.is_relative_to(root):
        return "저장소 밖"
    if not dest.exists():
        parts = dest.relative_to(root).parts
        inside_docs = parts[0] in SCANNED_DIRS or (len(parts) == 1 and dest.suffix == ".md")
        if inside_docs:
            return "경로 없음"
        return f"경로 없음 — 문서 밖 경로면 허용 목록({ALLOWLIST})에 그 경로가 있는지 확인한다"
    if anchor and dest.is_file() and dest.suffix == ".md" and anchor not in anchors_of(dest):
        return "앵커 없음"
    return None


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    root = Path(args[0]) if args else Path.cwd()
    problems, stats = check(root)
    for problem in problems:
        print(problem)
    print(
        f"검사: 마크다운 {stats.files} · 링크 {stats.links}"
        f"(상대 {stats.relative} · 앵커 {stats.anchored}) · 깨진 링크 {len(problems)}"
    )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
