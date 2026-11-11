"""스토리라인 결과 파일 형식 · 실행 종류."""

import json
import tempfile
import unittest
from datetime import date
from pathlib import Path

from labels import LabelError, Sample
from storyline_labels import Relation, Snapshot, StorylineSample
from storyline_result import RunKind, read_result, run_kind

SAMPLE = StorylineSample(Sample(1, date(2026, 11, 4), date(2026, 11, 5), "Asia/Seoul"),
                         Snapshot("fixed.dump", "f" * 64), Snapshot("lineage.dump", "e" * 64))
META = {"lineage": "e" * 64, "dump": "f" * 64, "method": "baseline", "createdAt": "2026-11-20T03:00:00Z",
        "eventCriteria": {"upper": 0.8}, "embeddingModel": "onnx/e5-small", "judgementModels": ["jev-a"],
        "linking": {"window": 14}}
RESULT = {"format": 1, "meta": META,
          "storylines": [{"id": "s1", "articles": [101, 102]}, {"id": "s2", "articles": [103]}],
          "relations": [{"source": "s1", "target": "s2", "kind": "FOLLOWS"}],
          "big": [{"storyline": "s1", "day": "2026-11-04"}]}


class StorylineResultTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "result.json"

    def read(self, value):
        self.path.write_text(json.dumps(value), encoding="utf-8")
        return read_result(self.path)

    def problems(self, value):
        with self.assertRaises(LabelError) as caught:
            self.read(value)
        return str(caught.exception)

    def test_valid_result(self):
        result = self.read(RESULT)
        self.assertEqual(result.meta, META)
        self.assertEqual(result.storylines, {"s1": frozenset({101, 102}), "s2": frozenset({103})})
        self.assertEqual(result.relations, frozenset({("s1", "s2", Relation.FOLLOWS)}))
        self.assertEqual(result.big, frozenset({("s1", date(2026, 11, 4))}))
        # 같은 계보 위 다른 실행은 고정 덤프 표지가 없다(null)
        self.assertEqual(self.read({**RESULT, "meta": {**META, "dump": None}}).meta["dump"], None)

    def test_problems_are_collected(self):
        meta = dict(META)
        del meta["linking"]
        message = self.problems({**RESULT, "meta": meta,
                                 "storylines": [{"id": "s1", "articles": [101]}, {"id": "s1", "articles": [102]},
                                                {"id": "s3", "articles": []}, {"id": "s4", "articles": [True]},
                                                {"id": "", "articles": [1]}, {"id": "s5", "articles": [7, 7]}],
                                 "relations": [{"source": "s1", "target": "s9", "kind": "FOLLOWS"},
                                               {"source": "s1", "target": "s1", "kind": "CONTAINS"},
                                               {"source": "s1", "target": "s9", "kind": "NEXT"}],
                                 "big": [{"storyline": "s9", "day": "2026-11-04"},
                                         {"storyline": "s1", "day": "11-04"}]})
        for expected in ("meta 에 linking 가 없다", "storylines[1]: id s1 가 둘이다", "스토리라인 s3 에 기사가 없다",
                         "storylines[3]: articles 가 기사 ID(정수) 배열이 아니다", "storylines[4]: id 가 빈 문자열",
                         "스토리라인 s5 에 같은 기사가 둘이다", "relations[0]: 스토리라인 s9 가 storylines 에 없다",
                         "relations[1]: 스토리라인 s1 가 자기 자신과 이어졌다", "relations[2]: kind 는 CONTAINS 이나 FOLLOWS 다",
                         "big[0]: 스토리라인 's9' 가 storylines 에 없다", "big[1]: 날짜를 읽을 수 없다"):
            self.assertIn(expected, message)

    def test_ends_of_another_type_are_problems(self):
        # 스토리라인 표지 · 종류 자리에 배열이 와도 멈춤 안내로 모은다(해시할 수 없는 값)
        message = self.problems({**RESULT, "relations": [{"source": ["s1"], "target": "s2", "kind": "FOLLOWS"},
                                                         {"source": "s1", "target": "s2", "kind": ["FOLLOWS"]}],
                                 "big": [{"storyline": ["s1"], "day": "2026-11-04"}]})
        for expected in ("relations[0]: 스토리라인 ['s1'] 가 storylines 에 없다", "relations[1]: kind 는 CONTAINS 이나 FOLLOWS 다",
                         "big[0]: 스토리라인 ['s1'] 가 storylines 에 없다"):
            self.assertIn(expected, message)

    def test_meta_shapes(self):
        message = self.problems({**RESULT, "meta": {**META, "eventCriteria": [], "judgementModels": "jev-a",
                                                    "createdAt": "2026-11-20 03:00", "lineage": ""}})
        for key in ("meta.eventCriteria", "meta.judgementModels", "meta.lineage", "meta.createdAt"):
            self.assertIn(key, message)

    def test_format_version(self):
        self.assertIn("형식 2 이다 — 이 도구는 1 이다", self.problems({**RESULT, "format": 2}))

    def test_run_kind(self):
        self.assertIs(run_kind(self.read(RESULT), SAMPLE), RunKind.SAME_DUMP)
        self.assertIs(run_kind(self.read({**RESULT, "meta": {**META, "dump": None}}), SAMPLE), RunKind.OTHER_RUN)
        self.assertIs(run_kind(self.read({**RESULT, "meta": {**META, "dump": "d" * 64}}), SAMPLE), RunKind.OTHER_RUN)
        with self.assertRaises(LabelError) as caught:
            run_kind(self.read({**RESULT, "meta": {**META, "lineage": "c" * 64}}), SAMPLE)
        self.assertIn("다른 계보의 결과는 기사 ID 가 다른 기사일 수 있다", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
