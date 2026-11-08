"""운영자 API 읽기 — 응답은 USED_FIELDS 를 거쳐서만 읽고(그래야 계약 대조 test_contract.py 가 실제로 읽는 필드를 덮는다),
읽기가 어떻게 실패해도 ApiError 로 멈춘다."""

import http.client
import io
import json
import unittest
from datetime import date

from admin_api import USED_FIELDS, AdminApi, ApiError, field

DAY = date(2026, 11, 4)
ZONE = "Asia/Seoul"
CRITERIA = {"windowSeconds": 259200, "upper": 0.80, "lower": 0.55, "store": 0.40, "probability": 0.50}


class FieldTest(unittest.TestCase):

    def test_listed_field_is_read(self):
        self.assertIn("articleId", USED_FIELDS["EventArticleResponse"])
        self.assertEqual(field({"articleId": 7}, "EventArticleResponse", "articleId"), 7)

    def test_unlisted_field_fails(self):
        with self.assertRaises(LookupError):
            field({"score": 0.9}, "EventArticleResponse", "score")
        with self.assertRaises(LookupError):
            field({"id": 1}, "NoSuchResponse", "id")

    def test_listed_field_missing_from_response(self):
        with self.assertRaises(ApiError):
            field({}, "EventArticleResponse", "articleId")


class Pages:
    """가짜 opener — 요청마다 다음 응답 본문을 낸다. 응답이 바닥나면 더 읽으려는 것이므로 시험을 실패시킨다."""

    def __init__(self, *pages):
        self.pages = list(pages)
        self.calls = 0

    def __call__(self, request, timeout):
        self.calls += 1
        if not self.pages:
            raise AssertionError(f"응답 {self.calls - 1}개를 넘겨 더 읽는다")
        page = self.pages.pop(0)
        if isinstance(page, BaseException):
            raise page
        return io.BytesIO(page if isinstance(page, bytes) else json.dumps(page).encode("utf-8"))


class Broken(io.BytesIO):
    """본문을 읽다가 끊기는 응답."""

    def __init__(self, error):
        super().__init__()
        self.error = error

    def read(self, *args):
        raise self.error


class SendTest(unittest.TestCase):

    def test_pages_are_read_to_the_end(self):
        # 대조 leg — 커서가 나아가면 쪽을 끝까지 읽는다
        pages = Pages({"criteria": CRITERIA, "judgements": [{"articleId": 1, "modelVersion": "jev-a"}],
                       "next": {"judgedAt": "2026-11-04T01:00:00Z", "id": 5}},
                      {"criteria": CRITERIA, "judgements": [{"articleId": 1, "modelVersion": "jev-b"}], "next": None})
        api = AdminApi("http://admin.invalid", "t", open_url=pages)
        self.assertEqual(api.judgement_models({1}, DAY, DAY, ZONE), {"jev-a", "jev-b"})
        self.assertEqual(pages.calls, 2)

    def test_judgement_cursor_must_advance(self):
        # 서버가 커서를 무시해 같은 쪽을 되풀이하면 끝없이 읽지 않고 멈춘다
        page = {"criteria": CRITERIA, "judgements": [], "next": {"judgedAt": "2026-11-04T01:00:00Z", "id": 5}}
        api = AdminApi("http://admin.invalid", "t", open_url=Pages(page, page, page))
        with self.assertRaises(ApiError) as caught:
            api.judgement_models({1}, DAY, DAY, ZONE)
        self.assertIn("커서", str(caught.exception))

    def test_failed_reads_are_api_errors(self):
        # 응답을 기다리다 시간이 다 됨 · 본문이 중간에 끊김 · JSON 이 아닌 본문 — 모두 종료 3 의 ApiError 다
        openers = {"응답 대기 시간 초과": Pages(TimeoutError("timed out")),
                   "본문 끊김": lambda request, timeout: Broken(http.client.IncompleteRead(b"{")),
                   "JSON 아님": Pages(b"<html>")}
        for name, opener in openers.items():
            api = AdminApi("http://admin.invalid", "t", open_url=opener)
            with self.subTest(name):
                with self.assertRaises(ApiError) as caught:
                    api.event_ids(DAY, DAY, ZONE)
                self.assertNotIn("연결 실패", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
