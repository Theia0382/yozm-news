"""도구의 라벨 기준 판이 운영 안내의 판 표기와 같은지 대조한다 — 판의 정본은 운영 안내다. 사건 묶기 평가(LABEL_RULE_VERSION)와
스토리라인 평가(STORYLINE_LABEL_RULE_VERSION)를 저마다의 운영 안내와 견준다."""

import re
import unittest
from pathlib import Path

from labels import LABEL_RULE_VERSION
from storyline_labels import STORYLINE_LABEL_RULE_VERSION

OPERATIONS = Path(__file__).resolve().parents[2] / "docs" / "operations"
GUIDE = OPERATIONS / "event-evaluation.md"
STORYLINE_GUIDE = OPERATIONS / "storyline-evaluation.md"
HEADING = re.compile(r"^## 라벨 기준 (\d+)판$", re.MULTILINE)


def guide_versions(text: str) -> list[int]:
    """운영 안내의 `## 라벨 기준 N판` 제목에서 읽은 판 — 하나여야 한다."""
    return [int(number) for number in HEADING.findall(text)]


class LabelRuleTest(unittest.TestCase):

    def test_tool_version_matches_guide(self):
        versions = guide_versions(GUIDE.read_text(encoding="utf-8"))
        self.assertEqual(versions, [LABEL_RULE_VERSION])

    def test_storyline_tool_version_matches_guide(self):
        versions = guide_versions(STORYLINE_GUIDE.read_text(encoding="utf-8"))
        self.assertEqual(versions, [STORYLINE_LABEL_RULE_VERSION])

    def test_heading_must_appear_exactly_once(self):
        # 대조 leg — 판 표기가 없거나 둘이면 위 대조가 성립하지 않는다
        self.assertEqual(guide_versions("## 라벨 기준 1판\n본문\n"), [1])
        self.assertEqual(guide_versions("# 제목\n본문에 라벨 기준 1판이라고 적어도 제목이 아니다\n"), [])
        self.assertEqual(guide_versions("## 라벨 기준 1판\n## 라벨 기준 2판\n"), [1, 2])


if __name__ == "__main__":
    unittest.main()
