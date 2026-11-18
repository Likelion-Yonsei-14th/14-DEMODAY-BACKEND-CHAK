package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** 수험표 할인 모음(혜택함)의 혜택 한 건. 운영자가 어드민에서 올리고 고친다. 날짜는 한국 날짜다. */
@Getter
@Entity
@Table(name = "discounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 200)
    private String benefit;

    /** 시작일. 없으면 이미 진행 중으로 본다. */
    private LocalDate startDate;

    /** 마지막 날. 없으면 끝나는 날을 모른다. */
    private LocalDate endDate;

    @Column(length = 1000)
    private String conditions;

    /** 걸러 보기용 시·도("서울") 또는 "전국". */
    @Column(nullable = false, length = 20)
    private String area;

    @Column(length = 100)
    private String place;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    private LocalDate sourceDate;

    @Column(length = 30)
    private String highlight;

    /** 챙길 것. 한 줄에 하나씩. */
    @Column(length = 500)
    private String bring;

    @Column(length = 50)
    private String howTo;

    /** 광고주마다 다른 이벤트 내용·조건·증정품. 한 줄에 하나씩 "이름<TAB>내용". */
    @Column(length = 2000)
    private String extras;

    @Column(length = 200)
    private String companions;

    @Column(length = 200)
    private String branches;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String imageSource;

    /** 꺼 두면 지우지 않고 목록에서만 내린다. */
    @Column(nullable = false)
    private boolean visible = true;

    @Column(length = 50)
    private String updatedByName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** 광고 계약한 매장 혜택. 목록 위에 따로 돌려 보여 주고 노출·클릭을 센다. */
    @Column(nullable = false)
    private boolean sponsored;

    /** 지도에 찍을 매장 주소와 좌표(WGS84). 좌표가 없으면 지도에 나오지 않는다. */
    @Column(length = 200)
    private String address;

    private Double latitude;

    private Double longitude;

    public Discount(String brand) {
        this.brand = brand;
    }

    public void update(String brand, String category, String benefit, LocalDate startDate, LocalDate endDate,
                       String conditions, String area, String place, String sourceUrl, LocalDate sourceDate,
                       String highlight, List<String> bring, String howTo, List<Extra> extras, String companions,
                       String branches, String imageUrl, String imageSource, boolean visible, String updatedByName) {
        this.brand = brand;
        this.category = category;
        this.benefit = benefit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.conditions = conditions;
        this.area = area;
        this.place = place;
        this.sourceUrl = sourceUrl;
        this.sourceDate = sourceDate;
        this.highlight = highlight;
        this.bring = bring == null || bring.isEmpty() ? null : String.join("\n", bring);
        this.howTo = howTo;
        this.extras = extras == null || extras.isEmpty() ? null
                : String.join("\n", extras.stream().map(extra -> extra.label() + "\t" + extra.value()).toList());
        this.companions = companions;
        this.branches = branches;
        this.imageUrl = imageUrl;
        this.imageSource = imageSource;
        this.visible = visible;
        this.updatedByName = updatedByName;
        this.updatedAt = Instant.now();
    }

    /** 제휴 광고 여부와 지도 위치. 좌표는 둘 다 있거나 둘 다 없다. */
    public void updatePlacement(boolean sponsored, String address, Double latitude, Double longitude) {
        this.sponsored = sponsored;
        this.address = address;
        boolean located = latitude != null && longitude != null;
        this.latitude = located ? latitude : null;
        this.longitude = located ? longitude : null;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public List<String> bringItems() {
        if (bring == null || bring.isBlank()) return List.of();
        return Arrays.stream(bring.split("\n")).map(String::strip).filter(item -> !item.isEmpty()).toList();
    }

    /** 추가 항목 한 줄. 이름("증정")과 내용("음료 1잔"). 둘 다 탭·줄바꿈이 없다. */
    public record Extra(String label, String value) {
    }

    /** 저장된 "이름<TAB>내용" 줄들을 읽는다. 탭이 없거나 한쪽이 빈 줄은 건너뛴다. */
    public List<Extra> extraItems() {
        if (extras == null || extras.isBlank()) return List.of();
        return Arrays.stream(extras.split("\n"))
                .map(line -> line.split("\t", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> new Extra(parts[0].strip(), parts[1].strip()))
                .filter(extra -> !extra.label().isEmpty() && !extra.value().isEmpty())
                .toList();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = this.createdAt;
    }
}
