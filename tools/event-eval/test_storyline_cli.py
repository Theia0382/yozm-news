"""스토리라인 평가 명령 끝까지 — 사건 평가 테스트의 가짜 운영자 API(test_cli.FakeAdmin)에 대해 돌린다. 기대값은 고정물에서
손으로 셈한 리터럴이다."""

import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from storyline_cli import main
from test_cli import TOKEN, ZONE, FakeAdmin

DUMP_SHA256 = "d23a270e3d8c876b29f7d4a11b8ddcc0c883a6abf35a161963bef36be50e7871"  # b"fixed-dump-bytes\n" — sha256sum
LINEAGE_SHA256 = "b4dc6e0605759e13b6890bdcf71f094ce548e1e33902fdb998e4bc0a2b7307a0"  # b"lineage-dump-bytes\n"

# FakeAdmin 의 사건 10(101 · 102) · 11(103 · 104 — 199 는 표본 밖) · 12(105 · 106 — 107 은 기사 정보 없음), 13 은 10 에
# 합쳐졌다. 시트는 첫 기사 시각 순, 소스는 사건 안에서 모아 정렬한다
SHEET = ("event_id,first_published_at,last_published_at,articles,sources,title\n"
         "10,2026-11-04T01:00:00Z,2026-11-04T02:00:00Z,2,경향;동아,첫 사건 기사 하나\n"
         "11,2026-11-04T03:00:00Z,2026-11-05T01:00:00Z,2,JTBC;SBS,첫 사건 기사 셋\n"
         "12,2026-11-05T02:00:00Z,2026-11-05T03:00:00Z,2,SBS;동아,오늘의 주요 뉴스\n")
SHEET_ARTICLES = ("event_id,article_id,published_at,sources,title\n"
                  "10,101,2026-11-04T01:00:00Z,동아,첫 사건 기사 하나\n"
                  "10,102,2026-11-04T02:00:00Z,경향,첫 사건 기사 둘\n"
                  "11,103,2026-11-04T03:00:00Z,SBS,첫 사건 기사 셋\n"
                  "11,104,2026-11-05T01:00:00Z,JTBC,둘째 사건 기사\n"
                  "12,105,2026-11-05T02:00:00Z,동아,오늘의 주요 뉴스\n"
                  "12,106,2026-11-05T03:00:00Z,SBS,라벨 밖 기사\n")

# random.Random(7).shuffle([10, 11, 12]) 를 인터프리터에서 직접 돌린 순서: [12, 10, 11]
REVIEW = """== x 첫 흐름 · 사건 2 · 큰 날짜 없음
  상위: y
  *  10 · 11-04 10:00 · 2건
      11-04 10:00 [동아] 첫 사건 기사 하나
      11-04 11:00 [경향] 첫 사건 기사 둘
  *  11 · 11-04 12:00 · 2건  (! 대조로 고침)
      11-04 12:00 [SBS] 첫 사건 기사 셋
      11-05 10:00 [JTBC] 둘째 사건 기사
== y 큰 흐름 · 사건 2 · 큰 날짜 2026-11-04
  포함: x
  *^ 10 · 11-04 10:00 · 2건
      11-04 10:00 [동아] 첫 사건 기사 하나
      11-04 11:00 [경향] 첫 사건 기사 둘
  *^ 11 · 11-04 12:00 · 2건  (! 대조로 고침)
      11-04 12:00 [SBS] 첫 사건 기사 셋
      11-05 10:00 [JTBC] 둘째 사건 기사
== 없음으로 적은 씨앗 1
  *  12 · 11-05 11:00 · 2건  (? 여러 일)
      11-05 11:00 [동아] 오늘의 주요 뉴스
      11-05 12:00 [SBS] 라벨 밖 기사
== ? · ! 메모 줄 2
  11 [x] ! 대조로 고침
  12 [-] ? 여러 일
"""


class StorylineCliTest(unittest.TestCase):

    def setUp(self):
        proxies = {key: "" for key in ("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy",
                                       "ALL_PROXY")}
        patcher = mock.patch.dict(os.environ, {**proxies, "no_proxy": "*", "NO_PROXY": "*"})
        patcher.start()
        self.addCleanup(patcher.stop)
        self.fake = FakeAdmin()
        self.addCleanup(self.fake.close)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.data = self.root / "sample"
        dumps = self.root / "dumps"
        dumps.mkdir()
        (dumps / "fixed.dump").write_bytes(b"fixed-dump-bytes\n")
        (dumps / "lineage.dump").write_bytes(b"lineage-dump-bytes\n")
        self.dump, self.lineage = dumps / "fixed.dump", dumps / "lineage.dump"

    def run_tool(self, *args, token=None):
        out, err = io.StringIO(), io.StringIO()
        code = main([*args, "--data", str(self.data)], environ={"YOZMNEWS_ADMIN_TOKEN": token} if token else {},
                    stdout=out, stderr=err)
        return code, out.getvalue(), err.getvalue()

    def sheet(self, token=TOKEN, dump=None):
        return self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE,
                             "--dump", str(dump or self.dump), "--lineage", str(self.lineage),
                             "--base-url", self.fake.url, token=token)

    def read(self, name):
        return (self.data / name).read_text(encoding="utf-8")

    def write(self, name, text):
        (self.data / name).write_text(text, encoding="utf-8")

    def test_sheet_writes_events_of_the_period(self):
        code, out, err = self.sheet()
        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(self.read("sheet.csv"), SHEET)
        self.assertEqual(self.read("sheet-articles.csv"), SHEET_ARTICLES)
        self.assertEqual(json.loads(self.read("sample.json")), {
            "kind": "storyline", "labelRule": 1, "start": "2026-11-04", "end": "2026-11-05", "zone": ZONE,
            "dump": {"name": "fixed.dump", "sha256": DUMP_SHA256},
            "lineage": {"name": "lineage.dump", "sha256": LINEAGE_SHA256}})
        self.assertEqual(out, f"시트 {self.data / 'sheet.csv'}: 사건 3 · 기사 6건(기사 정보가 없어 뺀 기사 1건, "
                              f"기간 밖이라 뺀 기사 1건)\n")
        # 다시 만들지 않는다 — 라벨을 붙인 시트를 덮지 않게
        code, _, err = self.sheet()
        self.assertEqual(code, 2)
        self.assertIn("sample.json 가 이미 있다 — 덮지 않는다", err)

    def test_sheet_needs_the_token_and_the_dump_files(self):
        code, _, err = self.sheet(token=None)
        self.assertEqual(code, 2)
        self.assertIn("YOZMNEWS_ADMIN_TOKEN", err)
        self.assertEqual(self.fake.requests, [])
        code, _, err = self.sheet(dump=self.root / "dumps" / "missing.dump")
        self.assertEqual(code, 2)
        self.assertIn("missing.dump 가 없다", err)
        self.assertFalse(self.data.exists())

    def test_article_in_two_events_stops_the_sheet(self):
        self.fake.events[12] = ("OPEN", [105, 106, 101])
        code, _, err = self.sheet()
        self.assertEqual(code, 2)
        self.assertIn("기사 101 가 사건", err)
        self.assertFalse(self.data.exists())

    def label(self):
        """시트 · 씨앗(7 — 셋) 뒤 라벨: y ⊃ x, 10 · 11 은 x, 12 는 없음. 큰 표시 y 11-04."""
        self.assertEqual(self.sheet()[0], 0)
        code, out, err = self.run_tool("seeds", "--count", "2", "--seed", "7")
        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(out, "씨앗 7: 씨앗 사건 2개(시트 사건 3개), labels.csv 에 더한 줄 2\n")
        self.assertEqual(json.loads(self.read("seeds.json")), {"seed": 7, "events": [12, 10]})
        self.assertEqual(self.read("labels.csv"), "event_id,storylines,note\n12,,\n10,,\n")
        self.write("labels.csv", "event_id,storylines,note\n12,-,? 여러 일\n10,x,\n")
        # 늘리기 — --seed 없이 있는 씨앗 값으로, 앞부분이 같고 적은 줄은 남는다
        code, out, err = self.run_tool("seeds", "--count", "3")
        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(out, "씨앗 7: 씨앗 사건 3개(시트 사건 3개), labels.csv 에 더한 줄 1\n")
        self.assertEqual(json.loads(self.read("seeds.json")), {"seed": 7, "events": [12, 10, 11]})
        self.assertEqual(self.read("labels.csv"), "event_id,storylines,note\n12,-,? 여러 일\n10,x,\n11,,\n")
        self.write("labels.csv", "event_id,storylines,note\n12,-,? 여러 일\n10,x,\n11,x,! 대조로 고침\n")
        self.write("storylines.csv", "storyline,name\nx,첫 흐름\ny,큰 흐름\n")
        self.write("relations.csv", "source,target,kind\ny,x,CONTAINS\n")
        self.write("big.csv", "storyline,day\ny,2026-11-04\n")

    def test_seeds_then_review(self):
        self.label()
        code, out, err = self.run_tool("review")
        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(out, REVIEW)

    def test_seeds_with_another_seed_value_stops(self):
        self.label()
        code, _, err = self.run_tool("seeds", "--count", "3", "--seed", "8")
        self.assertEqual(code, 2)
        self.assertIn("씨앗 값이 7 다 — 다른 씨앗 값 8 으로 바꾸지 않는다", err)
        self.assertEqual(json.loads(self.read("seeds.json")), {"seed": 7, "events": [12, 10, 11]})

    def test_seeds_without_a_seed_value_prints_the_one_it_chose(self):
        self.assertEqual(self.sheet()[0], 0)
        code, out, err = self.run_tool("seeds", "--count", "1")
        self.assertEqual((code, err), (0, ""), err)
        seed = json.loads(self.read("seeds.json"))["seed"]
        self.assertEqual(out, f"씨앗 {seed}: 씨앗 사건 1개(시트 사건 3개), labels.csv 에 더한 줄 1\n")

    def test_review_stops_on_labels_out_of_rule(self):
        self.label()
        self.write("labels.csv", "event_id,storylines,note\n12,-,\n10,x,\n11,,\n")
        code, out, err = self.run_tool("review")
        self.assertEqual((code, out), (2, ""))
        self.assertIn("사건 11 의 storylines 가 비었다", err)

    def test_storyline_commands_stop_on_an_event_sample(self):
        # 사건 평가의 표본(kind 없음)과 시트 — 판을 스토리라인 판과 같게 두어도 kind 로 멈춘다
        self.data.mkdir()
        self.write("sample.json", '{"labelRule": 1, "start": "2026-11-04", "end": "2026-11-05", "zone": "Asia/Seoul"}')
        code, _, err = self.run_tool("seeds", "--count", "1")
        self.assertEqual(code, 2)
        self.assertIn("스토리라인 평가의 표본이 아니다", err)
        self.assertFalse((self.data / "seeds.json").exists())

    def test_commands_write_only_inside_the_data_directory_and_only_sheet_calls_the_api(self):
        self.label()
        calls = len(self.fake.requests)
        self.assertGreater(calls, 0)  # 대조 — sheet 는 운영자 API 를 불렀다
        self.assertEqual(self.run_tool("review")[0], 0)
        self.assertEqual(self.run_tool("seeds", "--count", "3")[0], 2)  # 늘리지 않는 seeds 는 멈춘다
        # seeds · review 는 토큰 없이 돌았고 운영자 API 를 부르지 않았다
        self.assertEqual(len(self.fake.requests), calls)
        written = sorted(str(path.relative_to(self.root)) for path in self.root.rglob("*") if path.is_file())
        self.assertEqual(written, ["dumps/fixed.dump", "dumps/lineage.dump", "sample/big.csv", "sample/labels.csv",
                                   "sample/relations.csv", "sample/sample.json", "sample/seeds.json",
                                   "sample/sheet-articles.csv", "sample/sheet.csv", "sample/storylines.csv"])
        # 덤프 파일은 읽기만 한다
        self.assertEqual((self.root / "dumps" / "fixed.dump").read_bytes(), b"fixed-dump-bytes\n")


if __name__ == "__main__":
    unittest.main()
