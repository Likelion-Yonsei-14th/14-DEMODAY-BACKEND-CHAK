package com.likelion.chak.liner;

import java.util.List;

/** 라이너 API 중 착이 쓰는 두 가지: 웹 검색과 짧은 글 생성. 키가 없으면 enabled()가 false. */
public interface LinerApi {

    boolean enabled();

    /** 한국어·한국 기준 웹 검색. 실패하면 빈 목록. */
    List<SearchResult> search(String query, String dateRange, int maxResults);

    /** 시스템 지시와 대화로 답을 만든다. 실패하면 null. */
    String complete(String system, List<Turn> messages, int maxTokens);

    record SearchResult(String title, String url, String hostname, String description, String date) {
    }

    record Turn(String role, String content) {
    }
}
