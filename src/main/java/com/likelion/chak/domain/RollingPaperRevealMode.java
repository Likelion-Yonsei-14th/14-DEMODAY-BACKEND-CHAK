package com.likelion.chak.domain;

/** 자기 롤링페이퍼를 언제 볼 수 있는지. */
public enum RollingPaperRevealMode {
    /** 친구가 쓰는 대로 바로 본다. */
    INSTANT,
    /** 반을 만든 사람이 롤링페이퍼를 종료하면 본다. */
    ON_END
}
