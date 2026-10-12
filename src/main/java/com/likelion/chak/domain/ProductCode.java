package com.likelion.chak.domain;

/** 판매 상품과 서버가 정한 단가(원). 프론트의 표시 가격은 이 값과 같아야 한다. */
public enum ProductCode {
    CHARM_ACRYLIC(50),
    GEM(5),
    UNIVERSITY_CHARM(150),
    PENNANT(150),
    LOCKER_BULB(200),
    LOCKER_PAINT(200);

    private final int unitPrice;

    ProductCode(int unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int unitPrice() {
        return unitPrice;
    }
}
