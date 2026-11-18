package com.likelion.chak.dto;

import com.likelion.chak.domain.Discount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 수험표 할인 모음(혜택함). 공개 목록과 어드민 관리가 같은 모양을 쓴다. */
public final class DiscountDtos {

    private DiscountDtos() {
    }

    /** 광고주마다 다른 이벤트 내용·조건·증정품 한 줄("증정" / "음료 1잔"). 탭·줄바꿈은 저장 구분자라 받지 않는다. */
    public record DiscountExtra(
            @NotBlank @Size(max = 20) @Pattern(regexp = "[^\\t\\r\\n]*", message = "추가 항목 이름에는 줄바꿈을 넣을 수 없어요.") String label,
            @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\t\\r\\n]*", message = "추가 항목 내용에는 줄바꿈을 넣을 수 없어요.") String value) {

        static List<DiscountExtra> of(Discount d) {
            return d.extraItems().stream().map(extra -> new DiscountExtra(extra.label(), extra.value())).toList();
        }
    }

    public record DiscountRequest(
            @NotBlank @Size(max = 100) String brand,
            @NotBlank @Size(max = 20) String category,
            @NotBlank @Size(max = 200) String benefit,
            LocalDate start,
            LocalDate end,
            @Size(max = 1000) String conditions,
            @NotBlank @Size(max = 20) String area,
            @Size(max = 100) String place,
            @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "출처는 http(s) 주소여야 해요.") String sourceUrl,
            LocalDate sourceDate,
            @Size(max = 30) String highlight,
            @Size(max = 10) List<@Size(max = 100) String> bring,
            @Size(max = 50) String howTo,
            @Size(max = 8) List<@Valid DiscountExtra> extras,
            @Size(max = 200) String companions,
            @Size(max = 200) String branches,
            @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$", message = "이미지는 http(s) 주소여야 해요.") String image,
            @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$", message = "이미지 출처는 http(s) 주소여야 해요.") String imageSource,
            Boolean visible) {
    }

    public record DiscountResponse(
            Long id,
            String brand,
            String category,
            String benefit,
            LocalDate start,
            LocalDate end,
            String conditions,
            String area,
            String place,
            String sourceUrl,
            LocalDate sourceDate,
            String highlight,
            List<String> bring,
            String howTo,
            List<DiscountExtra> extras,
            String companions,
            String branches,
            String image,
            String imageSource,
            boolean visible,
            String updatedByName,
            Instant updatedAt) {

        /** 사용자에게 보여 줄 때는 고친 운영자 이름을 빼고 내보낸다. */
        public static DiscountResponse forPublic(Discount d) {
            DiscountResponse r = from(d);
            return new DiscountResponse(r.id(), r.brand(), r.category(), r.benefit(), r.start(), r.end(), r.conditions(),
                    r.area(), r.place(), r.sourceUrl(), r.sourceDate(), r.highlight(), r.bring(), r.howTo(), r.extras(),
                    r.companions(), r.branches(), r.image(), r.imageSource(), r.visible(), null, r.updatedAt());
        }

        public static DiscountResponse from(Discount d) {
            return new DiscountResponse(d.getId(), d.getBrand(), d.getCategory(), d.getBenefit(), d.getStartDate(),
                    d.getEndDate(), d.getConditions(), d.getArea(), d.getPlace(), d.getSourceUrl(), d.getSourceDate(),
                    d.getHighlight(), d.bringItems(), d.getHowTo(), DiscountExtra.of(d), d.getCompanions(),
                    d.getBranches(), d.getImageUrl(), d.getImageSource(), d.isVisible(), d.getUpdatedByName(),
                    d.getUpdatedAt());
        }
    }

    /** 공개 목록. updatedAt은 마지막으로 고친 때로, 화면에 "○월 ○일 기준"으로 보여 준다. */
    public record DiscountList(Instant updatedAt, List<DiscountResponse> items) {
    }
}
