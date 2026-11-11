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
from test_cli import CLOCK, TOKEN, ZONE, FakeAdmin

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
# 같은 덤프 결과 — s1 은 사건 10 · 11 을 기사로 펼친 것, s2 의 999 는 시트 밖. 관계 없음, 큰 표시 s1 11-04
SAME_RESULT = ('{"format": 1, "meta": {"lineage": "' + LINEAGE_SHA256 + '", "dump": "' + DUMP_SHA256 + '", '
               '"method": "baseline", "createdAt": "2026-11-20T03:00:00Z", "eventCriteria": {"upper": 0.8}, '
               '"embeddingModel": "onnx/e5-small", "judgementModels": ["jev-a"], "linking": {"window": 14}}, '
               '"storylines": [{"id": "s1", "articles": [101, 102, 103, 104]}, {"id": "s2", "articles": [105, 999]}], '
               '"relations": [], "big": [{"storyline": "s1", "day": "2026-11-04"}]}\n')
# 같은 계보의 다른 실행 — 기사 104 를 s2 로 옮겼다
OTHER_RESULT = (SAME_RESULT.replace('"' + DUMP_SHA256 + '"', "null")
                .replace("[101, 102, 103, 104]", "[101, 102, 103]").replace("[105, 999]", "[104, 105]"))

# sha256sum 으로 셈한 값 — label() 뒤의 평가 디렉터리 파일과 SAME_RESULT
FILE_SHA256 = {
    "sheet.csv": "212e9ba64ef9eddb44a20e4e1a8ef0ff56584d0a3c3adbddda9f2262b385d4a5",
    "sheet-articles.csv": "3d1da784e972845ab74a07a81d16ed445562288380231b2a2d6c4ded985222a3",
    "seeds.json": "bc3ccd2855504179ac288935e7bb615fa717ff66c7dc3d5ec8dc36d97d0cb4f3",
    "labels.csv": "1c1e8790ead07e3e92684251c607dd29cadaa7a9483755875c1a340865392cdc",
    "storylines.csv": "fd988af5971809251636526b8126c1f1892ce1be7facfb1792b1afa02165aa33",
    "relations.csv": "efe643c15af0db2e8b2d632d993df5595f6fda0f87f836894e1476c5af71b5d7",
    "big.csv": "8e850e5723618f91277ce941a66fb281d2de6527037628a90b13634e8e68a462",
    "result": "d718f57d3e98542c87476649345121feeb5956f2f4fb2167ba442fac74a9b0d4",
}

# OTHER_RESULT 의 대응: s1(101 · 102 · 103) · s2(104 · 105) 모두 x(같으면 표지 순 앞) — 사건 12 는 시스템만, y 는 대응 없음
AGAINST = """대조 other.json — 실행 종류 OTHER_RUN, 시트 밖 기사 0
== x 첫 흐름 · 대응 s1, s2
  시스템만 1
    12 · 11-05 11:00 · 2건 · 오늘의 주요 뉴스
  정답만 0
== y 큰 흐름 · 대응 없음
  시스템만 0
  정답만 2
    10 · 11-04 10:00 · 2건 · 첫 사건 기사 하나
    11 · 11-04 12:00 · 2건 · 첫 사건 기사 셋
"""

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
                    stdout=out, stderr=err, clock=lambda: CLOCK)
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

    def write_result(self, name, text):
        path = self.root / "candidate" / name
        path.parent.mkdir(exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def record(self):
        return json.loads(self.read("results/20261107-030000.json"))

    def test_measure_record(self):
        # 손 셈(사건 가중 — 기사마다 0.5): 정답 모든 층 10 · 11 = {x, y}, 12 는 없음. 시스템 s1 = {101 ~ 104}, s2 = {105}
        # ALL 정밀도 — 101 ~ 104 는 s1 동반 넷이 모두 min(1, 2) ÷ 1 = 1, 105 · 106 은 저 혼자 — 1
        # ALL 재현율 — 101 ~ 104 는 동반 넷이 min(1, 2) ÷ 2 = 0.5, 105 는 (1 + 0) ÷ 2, 106 도 0.5 — 0.5, F1 2/3
        # TOP · LEAF 는 10 · 11 의 표지가 하나(y · x) — 재현율 (4·0.5·1 + 2·0.5·0.5) ÷ 3 = 5/6, F1 10/11
        self.label()
        calls = len(self.fake.requests)
        result = self.write_result("same.json", SAME_RESULT)
        code, out, err = self.run_tool("measure", "--result", str(result), "--note", "app=abc123")
        self.assertEqual((code, err), (0, ""), err)
        record = self.record()
        self.assertEqual(record["format"], 1)
        self.assertEqual(record["measuredAt"], "2026-11-07T03:00:00Z")
        self.assertEqual(record["sample"], {"start": "2026-11-04", "end": "2026-11-05", "zone": ZONE, "labelRule": 1,
                                            "dump": {"name": "fixed.dump", "sha256": DUMP_SHA256},
                                            "lineage": {"name": "lineage.dump", "sha256": LINEAGE_SHA256}})
        self.assertEqual(record["files"], FILE_SHA256)
        self.assertEqual(record["result"], {"runKind": "SAME_DUMP", "meta": {
            "lineage": LINEAGE_SHA256, "dump": DUMP_SHA256, "method": "baseline", "createdAt": "2026-11-20T03:00:00Z",
            "eventCriteria": {"upper": 0.8}, "embeddingModel": "onnx/e5-small", "judgementModels": ["jev-a"],
            "linking": {"window": 14}}})
        self.assertEqual(record["counts"], {
            "sheetEvents": 3, "sheetArticles": 6, "seedEvents": 3, "seedArticles": 6, "seedsWithoutStoryline": 1,
            "labeledEvents": 3, "memberEvents": 2, "goldStorylines": {"ALL": 2, "TOP": 1, "LEAF": 1}, "relations": 1,
            "bigMarks": 1, "ambiguousEvents": 1, "againstEvents": 1, "systemStorylines": 2,
            "matchedSystemStorylines": 1, "resultArticlesOutsideSheet": 1,
            "bySource": {"JTBC": 1, "SBS": 2, "경향": 1, "동아": 2}, "byDay": {"2026-11-04": 3, "2026-11-05": 3}})
        self.assertEqual(record["settings"], {"farPairDays": 5, "matchFloor": 0.5, "bootstrapRounds": 1000,
                                              "bootstrapSeed": 20261111})
        self.assertEqual(record["notes"], {"app": "abc123"})
        views = record["metrics"]["views"]
        expected = {"ALL": (1.0, 0.5, 2 / 3), "TOP": (1.0, 5 / 6, 10 / 11), "LEAF": (1.0, 5 / 6, 10 / 11)}
        for view, (precision, recall, f1) in expected.items():
            event = views[view]["bCubed"]["event"]
            for key, value in (("precision", precision), ("recall", recall), ("f1", f1)):
                self.assertAlmostEqual(event[key], value, places=12, msg=f"{view} {key}")
        # 씨앗 사건 셋의 ALL 정밀도 · 재현율이 모두 같아 부트스트랩 구간이 값에 붙는다
        for key, value in (("precision", 1.0), ("recall", 0.5), ("f1", 2 / 3)):
            for bound in views["ALL"]["bCubed"]["event"]["interval"][key]:
                self.assertAlmostEqual(bound, value, places=12)
        article = views["ALL"]["bCubed"]["article"]
        self.assertAlmostEqual(article["precision"], 1.0, places=12)
        self.assertAlmostEqual(article["recall"], 0.5, places=12)
        # 정답 스토리라인별 대응 — ALL 은 x · y 둘, TOP 은 y 하나 · LEAF 는 x 하나, 모두 s1 이 같은 기사
        self.assertEqual([views[view]["matches"]["storylines"] for view in ("ALL", "TOP", "LEAF")], [2, 1, 1])
        for bound in ("mean", "min"):
            for value in views["ALL"]["matches"][bound].values():
                self.assertAlmostEqual(value, 1.0, places=12)
        # 이틀 표본 — 5일 이상 쌍이 없다
        self.assertEqual(views["ALL"]["farPairs"], {"recall": None, "pairs": 0})
        # s1 은 x 에 대응(자카드 x · y 같음 — 표지 순 앞). 정답 관계 y ⊃ x 는 결과에 관계가 없어 못 찾음
        self.assertEqual(record["metrics"]["relations"], {"judged": 0, "correct": 0, "unmapped": 0, "precision": None,
                                                          "gold": 1, "found": 0, "recall": 0.0})
        # 큰 표시 (s1 → x, 11-04) 는 정답 (y, 11-04) 와 달라 틀림 — 0/1, Wilson 상한은 산식을 따로 셈한 값
        big = record["metrics"]["big"]
        self.assertEqual((big["judged"], big["correct"], big["outside"], big["precision"]), (1, 0, 0, 0.0))
        self.assertEqual(big["interval"][0], 0.0)
        self.assertAlmostEqual(big["interval"][1], 0.793451, places=6)
        self.assertIn("기록 ", out)
        self.assertIn("주의 — 결과 파일의 시트 밖 기사 1", out)
        # 운영자 API 를 부르지 않았고 토큰 없이 돌았다(run_tool 은 토큰을 주지 않는다), 기록에 제목이 없다
        self.assertEqual(len(self.fake.requests), calls)
        self.assertNotIn("첫 사건 기사", self.read("results/20261107-030000.json"))

    def test_other_run_is_measured_by_article(self):
        self.label()
        same = self.write_result("same.json", SAME_RESULT)
        other = self.write_result("other.json", OTHER_RESULT)
        self.assertEqual(self.run_tool("measure", "--result", str(same))[0], 0)
        same_precision = self.record()["metrics"]["views"]["ALL"]["bCubed"]["event"]["precision"]
        (self.data / "results" / "20261107-030000.json").unlink()
        code, _, err = self.run_tool("measure", "--result", str(other))
        self.assertEqual((code, err), (0, ""), err)
        record = self.record()
        self.assertEqual(record["result"]["runKind"], "OTHER_RUN")
        # 기사 104 하나를 옮긴 결과는 값이 다르다 — 104 의 정밀도가 (0.5 + 0) ÷ 1
        self.assertNotEqual(record["metrics"]["views"]["ALL"]["bCubed"]["event"]["precision"], same_precision)

    def test_result_of_another_lineage_stops(self):
        self.label()
        result = self.write_result("far.json", SAME_RESULT.replace(LINEAGE_SHA256, "c" * 64))
        code, _, err = self.run_tool("measure", "--result", str(result))
        self.assertEqual(code, 2)
        self.assertIn("다른 계보의 결과는 기사 ID 가 다른 기사일 수 있다", err)
        self.assertFalse((self.data / "results").exists())
        code, _, err = self.run_tool("review", "--against", str(result))
        self.assertEqual(code, 2)
        self.assertIn("다른 계보", err)

    def test_review_against_a_result(self):
        self.label()
        code, out, err = self.run_tool("review", "--against", str(self.write_result("other.json", OTHER_RESULT)))
        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(out, AGAINST)

    def test_measure_and_review_write_only_the_record_inside_the_data_directory(self):
        self.label()
        same = self.write_result("same.json", SAME_RESULT)
        before = sorted(str(path.relative_to(self.root)) for path in self.root.rglob("*") if path.is_file())
        calls = len(self.fake.requests)
        self.assertEqual(self.run_tool("review", "--against", str(same))[0], 0)
        self.assertEqual(self.run_tool("measure", "--result", str(same))[0], 0)
        after = sorted(str(path.relative_to(self.root)) for path in self.root.rglob("*") if path.is_file())
        self.assertEqual(sorted(set(after) - set(before)), ["sample/results/20261107-030000.json"])
        self.assertEqual(len(self.fake.requests), calls)


if __name__ == "__main__":
    unittest.main()
