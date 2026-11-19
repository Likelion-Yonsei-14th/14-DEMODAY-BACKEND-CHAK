package com.likelion.chak.service;

import com.likelion.chak.domain.Discount;
import com.likelion.chak.liner.LinerApi;
import com.likelion.chak.liner.LinerApi.SearchResult;
import com.likelion.chak.liner.LinerApi.Turn;
import com.likelion.chak.repository.DiscountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 웹에서 새 수험생·수험표 혜택을 찾아 혜택함에 "숨김" 후보로 넣는다. 운영진이 혜택함 관리에서 확인하고 보이게 바꿔야 공개된다.
 * 라이너 검색 결과에 있는 글만 쓰고(출처 주소가 결과 중 하나여야 한다), 이미 있는 매장·출처는 건너뛴다.
 */
@Service
public class DiscountCollector {

    private static final Logger log = LoggerFactory.getLogger(DiscountCollector.class);
    public static final String COLLECTOR_NAME = "자동 수집";
    static final int MAX_NEW = 15;
    static final List<String> QUERIES = List.of(
            "수능 수험표 할인 이벤트",
            "수험생 할인 수능 끝 이벤트",
            "수험표 할인 카페 음식점",
            "수험표 할인 영화 공연",
            "수험생 할인 미용실 퍼스널컬러 뷰티",
            "수험표 할인 놀이공원 체험",
            "수험생 안경 렌즈 할인",
            "수험생 여행 숙박 할인");
    static final Set<String> CATEGORIES = Set.of(
            "놀이·체험", "음식·카페", "뷰티·미용", "안경·렌즈", "영화·공연", "여행·숙박", "패션·쇼핑", "운동·건강", "교육");
    static final Set<String> AREAS = Set.of("전국", "서울", "경기", "인천", "강원", "대전", "세종", "충북", "충남", "광주",
            "전북", "전남", "대구", "경북", "부산", "울산", "경남", "제주");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final LinerApi liner;
    private final DiscountRepository discounts;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DiscountCollector(LinerApi liner, DiscountRepository discounts) {
        this(liner, discounts, Clock.systemUTC());
    }

    DiscountCollector(LinerApi liner, DiscountRepository discounts, Clock clock) {
        this.liner = liner;
        this.discounts = discounts;
        this.clock = clock;
    }

    public record Result(boolean enabled, int searched, List<String> added) {
    }

    @Transactional
    public Result collect() {
        if (!liner.enabled()) return new Result(false, 0, List.of());
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        List<Discount> existing = discounts.findAll();
        Set<String> knownUrls = new HashSet<>();
        Set<String> knownBrands = new HashSet<>();
        for (Discount d : existing) {
            if (d.getSourceUrl() != null) knownUrls.add(d.getSourceUrl().strip());
            knownBrands.add(normalize(d.getBrand()));
        }

        Map<String, SearchResult> fresh = new LinkedHashMap<>();
        for (String query : QUERIES) {
            for (SearchResult r : liner.search(query, "past_month", 10)) {
                if (r.url() == null || !r.url().startsWith("http") || knownUrls.contains(r.url())) continue;
                fresh.putIfAbsent(r.url(), r);
            }
        }
        if (fresh.isEmpty()) return new Result(true, 0, List.of());

        String raw = liner.complete(extractSystem(today, knownBrands), List.of(new Turn("user", listing(fresh.values()))), 3000);
        List<String> added = new ArrayList<>();
        if (raw == null) return new Result(true, fresh.size(), added);
        JsonNode items;
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) return new Result(true, fresh.size(), added);
            items = JSON.readTree(raw.substring(start, end + 1));
        } catch (RuntimeException e) {
            log.warn("Discount collector could not read the extraction");
            return new Result(true, fresh.size(), added);
        }
        for (JsonNode item : items) {
            if (added.size() >= MAX_NEW) break;
            String brand = clip(text(item, "brand"), 60);
            String benefit = clip(text(item, "benefit"), 200);
            String url = text(item, "sourceUrl");
            if (brand.isEmpty() || benefit.isEmpty() || !fresh.containsKey(url) || knownBrands.contains(normalize(brand))) continue;
            LocalDate start = date(text(item, "startDate"));
            LocalDate end = date(text(item, "endDate"));
            if (end != null && end.isBefore(today)) continue;
            if (start != null && end != null && end.isBefore(start)) start = null;
            String category = CATEGORIES.contains(text(item, "category")) ? text(item, "category") : "놀이·체험";
            String area = AREAS.contains(text(item, "area")) ? text(item, "area") : "전국";
            Discount d = new Discount(brand);
            d.update(brand, category, benefit, start, end, clip(text(item, "conditions"), 300), area,
                    clip(text(item, "place"), 100), url, today, null, List.of(), null, List.of(), null, null, null, null,
                    false, COLLECTOR_NAME);
            discounts.save(d);
            knownBrands.add(normalize(brand));
            added.add(brand);
        }
        log.info("Discount collector searched {} pages and added {} hidden candidates", fresh.size(), added.size());
        return new Result(true, fresh.size(), added);
    }

    private static String extractSystem(LocalDate today, Set<String> knownBrands) {
        return """
                너는 수험생 할인 정보를 정리하는 도우미다. 오늘은 %s이다. 사용자가 준 검색 결과에서 '2027학년도 수능을 본 수험생(수험표 지참)'에게
                주는 실제 할인·혜택만 골라 JSON 배열 하나만 출력한다. 다른 글은 쓰지 않는다.
                [{"brand":"매장·브랜드 이름","category":"%s 중 하나","benefit":"혜택 한 줄(예: 음료 30%% 할인)","area":"전국 또는 시·도(서울, 경기, 부산 …)",
                  "place":"지점·장소(모르면 빈 문자열)","startDate":"YYYY-MM-DD 또는 null","endDate":"YYYY-MM-DD 또는 null","conditions":"조건(모르면 빈 문자열)",
                  "sourceUrl":"근거가 된 검색 결과의 url 그대로"}]
                규칙: 검색 결과에 적힌 내용만 쓴다. 지난해 이전 이벤트, 이미 끝난 이벤트, 할인 내용이 없는 글, 모음 기사 자체는 빼고
                기사 안의 개별 매장 혜택으로 나눈다. 다음 브랜드는 이미 있으니 빼라: %s. 없으면 []를 출력한다.
                """.formatted(today, String.join(", ", CATEGORIES), String.join(", ", knownBrands.stream().limit(200).toList()));
    }

    private static String listing(Iterable<SearchResult> results) {
        StringBuilder b = new StringBuilder("검색 결과:\n");
        for (SearchResult r : results) {
            b.append("- url: ").append(r.url()).append("\n  제목: ").append(clip(r.title(), 150))
                    .append("\n  날짜: ").append(r.date() == null ? "" : r.date())
                    .append("\n  내용: ").append(clip(r.description(), 500)).append('\n');
        }
        return b.toString();
    }

    static String normalize(String brand) {
        return brand == null ? "" : brand.replaceAll("[\\s·.,()\\-_]", "").toLowerCase(Locale.ROOT);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() ? v.asString().strip() : "";
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
