package com.likelion.chak.domain;

/** 롤링페이퍼 종류. */
public enum RollingPaperKind {
    /** 반: 들어온 사람마다 사물함에 자기 종이를 하나씩 걸고 서로 쓴다. */
    CLASS,
    /** 개인: 만든 사람의 종이 한 장. 링크로 온 사람은 쓰기만 하고, 남이 쓴 글은 주인만 읽는다. */
    PERSONAL
}
