"""check_links 의 단위 테스트 — 기대값은 GitHub 의 앵커 규칙으로 손으로 셈한 리터럴이다."""

import io
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import check_links


def write(root: Path, rel: str, text: str = "") -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class RepoTestCase(unittest.TestCase):
    """임시 저장소 — 스캔 하한을 넘도록 docs/README.md 를 먼저 둔다."""

    def setUp(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        self.root = Path(self._dir.name)
        write(self.root, "docs/README.md", "# 문서\n")

    def tearDown(self) -> None:
        self._dir.cleanup()

    def problems(self) -> list[tuple[str, int | None, str, str]]:
        problems, _ = check_links.check(self.root)
        return [(p.path, p.line, p.target, p.reason) for p in problems]


class AnchorTest(unittest.TestCase):
    """제목 → 앵커."""

    def test_heading_to_anchor(self) -> None:
        cases = {
            "## 3. 갱신 트리거": "3-갱신-트리거",
            "## 피드 XML 입력": "피드-xml-입력",
            "### 소스 등록 ([ADR-0015](x.md) · [ADR-0024](y.md))": "소스-등록-adr-0015--adr-0024",
            "## `ArticleSource` 포트": "articlesource-포트",
            "## 5. PR": "5-pr",
        }
        for heading, anchor in cases.items():
            with self.subTest(heading=heading):
                self.assertEqual(check_links.heading_anchors(heading + "\n"), [anchor])

    def test_kept_characters_follow_github_word_class(self) -> None:
        # ① 은 십진 숫자가 아닌 숫자(No)라 빠지고, 낱말 안의 _ 는 연결 구두점(Pc)이라 남으며,
        # 밑줄 강조 표시는 글자가 아니라 떨어진다.
        cases = {
            "## 단계 ① 정리": "단계--정리",
            "## snake_case 와 __init__": "snake_case-와-init",
        }
        for heading, anchor in cases.items():
            with self.subTest(heading=heading):
                self.assertEqual(check_links.heading_anchors(heading + "\n"), [anchor])

    def test_duplicate_headings_get_suffix(self) -> None:
        self.assertEqual(check_links.heading_anchors("# a\n## a\n### a\n"), ["a", "a-1", "a-2"])

    def test_closing_hashes_are_not_heading_text(self) -> None:
        self.assertEqual(check_links.heading_anchors("## 범위 ##\n"), ["범위"])


class CodeTest(RepoTestCase):
    """코드 안의 링크 · 제목."""

    def test_links_in_code_are_ignored(self) -> None:
        write(
            self.root,
            "docs/a.md",
            "```\n[x](없는.md)\n```\n~~~~md\n[y](없는.md)\n~~~~\n본문 `[z](없는.md)` 끝\n",
        )
        self.assertEqual(self.problems(), [])

    def test_link_after_code_block_is_checked(self) -> None:
        write(self.root, "docs/a.md", "```\n[x](없는.md)\n```\n[y](없는.md)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 4, "없는.md", "경로 없음")])

    def test_heading_in_code_block_is_not_an_anchor(self) -> None:
        write(self.root, "docs/a.md", "```\n## 가짜\n```\n[x](#가짜)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 4, "#가짜", "앵커 없음")])


class SchemeTest(RepoTestCase):
    """스킴 링크는 판정하지 않는다."""

    def test_scheme_links_are_skipped(self) -> None:
        write(self.root, "docs/a.md", "[a](https://example.com/없음#x) [b](mailto:a@example.com)\n")
        problems, stats = check_links.check(self.root)
        self.assertEqual(problems, [])
        self.assertEqual((stats.links, stats.relative), (2, 0))


class PathTest(RepoTestCase):
    """경로 판정."""

    def test_existing_file_and_directory_pass(self) -> None:
        write(self.root, "backend/build.gradle.kts")
        write(self.root, "docs/a.md", "[f](../backend/build.gradle.kts) [d](../backend/) [r](README.md)\n")
        self.assertEqual(self.problems(), [])

    def test_missing_path_is_broken(self) -> None:
        write(self.root, "docs/a.md", "[x](b.md)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 1, "b.md", "경로 없음")])

    def test_missing_path_outside_docs_names_allowlist(self) -> None:
        write(self.root, "docs/a.md", "[x](../scripts/run.sh)\n")
        reason = self.problems()[0][3]
        self.assertIn("tools/docs-check/Dockerfile.dockerignore", reason)

    def test_percent_encoding_is_decoded(self) -> None:
        write(self.root, "docs/한 글.md", "# 제목\n")
        write(self.root, "docs/a.md", "[x](%ED%95%9C%20%EA%B8%80.md#%EC%A0%9C%EB%AA%A9)\n")
        self.assertEqual(self.problems(), [])

    def test_path_outside_repository_is_broken(self) -> None:
        write(self.root, "docs/a.md", "[x](../../밖.md)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 1, "../../밖.md", "저장소 밖")])


class AnchorJudgeTest(RepoTestCase):
    """앵커 판정."""

    def test_existing_anchor_passes(self) -> None:
        write(self.root, "docs/b.md", "## 3. 갱신 트리거\n")
        write(self.root, "docs/a.md", "[x](b.md#3-갱신-트리거)\n")
        self.assertEqual(self.problems(), [])

    def test_missing_anchor_is_broken(self) -> None:
        write(self.root, "docs/b.md", "## 3. 갱신 트리거\n")
        write(self.root, "docs/a.md", "[x](b.md#갱신-트리거)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 1, "b.md#갱신-트리거", "앵커 없음")])

    def test_same_file_anchor(self) -> None:
        write(self.root, "docs/a.md", "## 범위\n[ok](#범위) [no](#없음)\n")
        self.assertEqual(self.problems(), [("docs/a.md", 2, "#없음", "앵커 없음")])

    def test_anchor_on_non_markdown_checks_path_only(self) -> None:
        write(self.root, "backend/A.java")
        write(self.root, "docs/a.md", "[x](../backend/A.java#L10)\n")
        self.assertEqual(self.problems(), [])


class OutputTest(RepoTestCase):
    """결과 형식과 종료 코드."""

    def run_main(self) -> tuple[int, str]:
        out = io.StringIO()
        with redirect_stdout(out):
            code = check_links.main([str(self.root)])
        return code, out.getvalue()

    def test_broken_link_prints_location_and_exits_1(self) -> None:
        write(self.root, "docs/a.md", "본문\n[x](b.md)\n")
        code, out = self.run_main()
        self.assertEqual(code, 1)
        self.assertIn("docs/a.md:2: b.md — 경로 없음\n", out)
        self.assertIn("깨진 링크 1", out)

    def test_clean_tree_exits_0(self) -> None:
        write(self.root, "docs/a.md", "[x](README.md)\n")
        code, out = self.run_main()
        self.assertEqual(code, 0)
        self.assertEqual(out, "검사: 마크다운 2 · 링크 1(상대 1 · 앵커 0) · 깨진 링크 0\n")


class ScanFloorTest(unittest.TestCase):
    """스캔 하한."""

    def setUp(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        self.root = Path(self._dir.name)

    def tearDown(self) -> None:
        self._dir.cleanup()

    def test_no_markdown_fails(self) -> None:
        problems, stats = check_links.check(self.root)
        self.assertEqual(stats.files, 0)
        self.assertEqual(len(problems), 1)
        self.assertIn("검사할 마크다운이 없다", problems[0].reason)

    def test_tree_without_docs_readme_fails(self) -> None:
        write(self.root, "README.md", "# 저장소\n")
        problems, _ = check_links.check(self.root)
        self.assertEqual([(p.path, p.line) for p in problems], [("docs/README.md", None)])

    def test_docs_readme_alone_passes(self) -> None:
        write(self.root, "docs/README.md", "# 문서\n")
        problems, stats = check_links.check(self.root)
        self.assertEqual(problems, [])
        self.assertEqual(stats.files, 1)


class UnsupportedTest(RepoTestCase):
    """지원하지 않는 링크 형식."""

    def test_reference_definition_and_html_href_fail(self) -> None:
        write(self.root, "docs/a.md", "본문 [r]\n\n[r]: x.md\n<a href=\"x.md\">x</a>\n")
        self.assertEqual(
            self.problems(),
            [
                ("docs/a.md", 3, "[r]: x.md", "지원하지 않는 링크 형식"),
                ("docs/a.md", 4, '<a href="x.md">x</a>', "지원하지 않는 링크 형식"),
            ],
        )

    def test_same_lines_in_code_block_pass(self) -> None:
        write(self.root, "docs/a.md", "```\n[r]: x.md\n<a href=\"x.md\">x</a>\n```\n")
        self.assertEqual(self.problems(), [])

    def test_root_relative_path_fails(self) -> None:
        write(self.root, "docs/a.md", "[x](/docs/README.md)\n")
        self.assertEqual(
            self.problems(),
            [("docs/a.md", 1, "/docs/README.md", "지원하지 않는 링크 형식 — 저장소 루트 기준 경로")],
        )


if __name__ == "__main__":
    unittest.main()
