package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 혜택 시안 신청서(프런트 {@code benefitSurvey})의 답변을 검증한다. 알려진 칸만 골라 담고, 고른 선택지에 딸린 칸이 비면 거절한다.
 * 선택지는 한글 문구가 아니라 코드로 받아, 화면 문구를 바꿔도 저장된 값이 깨지지 않는다.
 */
@Component
public class BenefitAnswerValidator {

    public static final String FILE_KEY_PREFIX = "promotion/uploads/";
    private static final Pattern FILE_KEY = Pattern.compile("^promotion/uploads/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[A-Za-z0-9._-]{1,80}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-() ]{7,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    static final Set<String> CATEGORIES = Set.of("CAFE", "FOOD", "BEAUTY_SALON", "COSMETICS", "FASHION", "OPTICAL", "PHOTO",
            "THEME_PARK", "CULTURE", "ENTERTAINMENT", "FITNESS", "TRAVEL", "ELECTRONICS", "TELECOM", "FINANCE", "OTT",
            "EDUCATION", "JOBS", "OTHER");
    static final Set<String> TARGETS = Set.of("CHAK_PARTICIPANT", "SNS_EVENT", "EXAM_TICKET_HOLDER", "OTHER");
    static final Set<String> BENEFITS = Set.of("PERCENT", "AMOUNT", "SPECIAL_PRICE", "TICKET_COUPON", "FREE_GIFT", "UPGRADE",
            "PLUS_ONE", "POINTS", "OTHER");
    static final Set<String> USAGE_METHODS = Set.of("SHOW_EXAM_TICKET", "SHOW_CHAK_SCREEN", "REGISTER_IN_APP", "MALL_COUPON_CODE",
            "BOOKING_COUPON_CODE", "BOX_OFFICE", "DEDICATED_LINK", "OTHER");
    static final Set<String> CODE_METHODS = Set.of("REGISTER_IN_APP", "MALL_COUPON_CODE", "BOOKING_COUPON_CODE");
    static final Set<String> BOOKING_CHANNELS = Set.of("APP", "WEB", "KIOSK");
    static final Set<String> COUPON_ISSUES = Set.of("SHARED_CODE", "UNIQUE_CODES", "ISSUED_BY_BRAND");
    static final Set<String> CONDITIONS = Set.of("EXAM_TICKET", "ID_CHECK", "CHAK_PARTICIPATION", "SNS_PARTICIPATION", "ONCE_PER_PERSON",
            "COMPANION_LIMIT", "MIN_PURCHASE", "LIMITED_QUANTITY", "MEMBER_SIGNUP", "SPECIFIC_ITEMS", "BLACKOUT", "NO_STACKING", "OTHER");
    static final Set<String> SCOPES = Set.of("ALL_STORES", "SOME_STORES", "ONLINE_ONLY");
    static final Set<String> EXPOSURES = Set.of("BENEFIT_BOX", "OFFICIAL_SNS", "PROPOSAL");
    static final Set<String> REVIEW_PERIODS = Set.of("WITHIN_1_DAY", "DAYS_2_3", "WEEK_PLUS");

    private final ObjectMapper mapper;

    public BenefitAnswerValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 검증을 통과한 칸만 담은 새 객체를 돌려준다. 어디가 잘못됐는지는 로그에 남기지 않고 한 가지 오류로만 알린다. */
    public ObjectNode validate(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            throw invalid();
        }
        Reader in = new Reader(raw, mapper.createObjectNode());

        in.text("brand", 100, true);
        in.choice("category", CATEGORIES, true);
        in.text("contactName", 50, true);
        in.pattern("phone", PHONE);
        in.pattern("email", EMAIL);
        require(in.out.path("email").asString("").length() <= 320);

        in.choice("target", TARGETS, true);
        if ("OTHER".equals(in.out.path("target").asString())) {
            in.text("targetOther", 100, true);
        }
        Set<String> benefits = in.choices("benefits", BENEFITS, true);
        if (benefits.contains("PERCENT")) in.integer("percent", 1, 100, true);
        if (benefits.contains("AMOUNT")) in.integer("amount", 1, 100_000_000, true);
        if (benefits.contains("SPECIAL_PRICE")) {
            long regular = in.integer("regularPrice", 1, 100_000_000, true);
            long special = in.integer("specialPrice", 1, 100_000_000, true);
            require(special < regular);
        }
        if (benefits.contains("TICKET_COUPON")) in.text("couponDetail", 200, true);
        if (benefits.contains("FREE_GIFT")) in.text("giftItem", 200, true);
        if (benefits.contains("UPGRADE")) in.text("upgradeDetail", 200, true);
        if (benefits.contains("PLUS_ONE")) {
            in.integer("maxCompanions", 1, 1000, true);
            in.text("companionDetail", 200, true);
        }
        if (benefits.contains("POINTS")) in.text("pointDetail", 200, true);
        if (benefits.contains("OTHER")) in.text("otherBenefitDetail", 200, true);
        in.text("headlineRaw", 20, true);
        in.text("benefitDetail", 1000, true);
        LocalDate start = in.date("startDate");
        LocalDate end = in.date("endDate");
        require(!end.isBefore(start));

        Set<String> usage = in.choices("usageMethods", USAGE_METHODS, true);
        if (usage.contains("REGISTER_IN_APP")) {
            in.text("appName", 100, true);
            in.text("couponPath", 200, false);
        }
        if (usage.contains("MALL_COUPON_CODE")) in.url("mallUrl", false);
        if (usage.contains("BOOKING_COUPON_CODE")) in.choices("bookingChannels", BOOKING_CHANNELS, false);
        if (usage.contains("DEDICATED_LINK")) in.url("dedicatedUrl", true);
        if (usage.contains("OTHER")) in.text("otherUsageDetail", 200, true);
        if (usage.stream().anyMatch(CODE_METHODS::contains)) {
            String issue = in.choice("couponIssue", COUPON_ISSUES, true);
            switch (issue) {
                case "SHARED_CODE" -> in.text("couponCode", 100, true);
                case "UNIQUE_CODES" -> {
                    in.integer("couponQuantity", 1, 10_000_000, true);
                    in.file("couponFileKey", true);
                }
                default -> in.url("couponGuideUrl", false);
            }
        }

        Set<String> conditions = in.choices("conditions", CONDITIONS, false);
        if (conditions.contains("SNS_PARTICIPATION")) in.text("snsMethod", 200, false);
        if (conditions.contains("COMPANION_LIMIT")) in.integer("conditionMaxCompanions", 1, 1000, true);
        if (conditions.contains("MIN_PURCHASE")) in.integer("conditionMinAmount", 1, 100_000_000, true);
        if (conditions.contains("LIMITED_QUANTITY")) in.integer("conditionQuantity", 1, 10_000_000, true);
        if (conditions.contains("SPECIFIC_ITEMS")) in.text("conditionItems", 300, true);
        if (conditions.contains("BLACKOUT")) in.text("conditionExcluded", 300, true);
        if (conditions.contains("OTHER")) in.text("conditionOther", 300, true);
        in.text("requiredNotice", 1000, false);

        String scope = in.choice("scope", SCOPES, true);
        if ("SOME_STORES".equals(scope)) {
            in.text("storeList", 3000, true);
            in.file("storeFileKey", false);
        }
        in.file("logoFileKey", true);
        in.choices("exposure", EXPOSURES, true);
        in.choice("reviewPeriod", REVIEW_PERIODS, true);
        in.text("officialSns", 100, false);
        in.text("message", 2000, false);
        return in.out;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.INVALID_BENEFIT_ANSWER);
    }

    /** 답변 한 칸씩 읽어 검증하고 통과한 값을 {@code out}에 옮겨 담는다. */
    private static final class Reader {
        private final JsonNode in;
        private final ObjectNode out;

        Reader(JsonNode in, ObjectNode out) {
            this.in = in;
            this.out = out;
        }

        private JsonNode node(String key) {
            JsonNode value = in.path(key);
            return value.isMissingNode() || value.isNull() ? null : value;
        }

        String text(String key, int max, boolean required) {
            JsonNode value = node(key);
            String text = value == null ? "" : value.isString() ? value.asString().strip() : null;
            require(text != null);
            if (text.isEmpty()) {
                require(!required);
                return "";
            }
            require(text.length() <= max);
            out.put(key, text);
            return text;
        }

        void pattern(String key, Pattern pattern) {
            String text = text(key, 320, true);
            require(pattern.matcher(text).matches());
        }

        String choice(String key, Set<String> allowed, boolean required) {
            String value = text(key, 40, required);
            require(value.isEmpty() ? !required : allowed.contains(value));
            return value;
        }

        Set<String> choices(String key, Set<String> allowed, boolean required) {
            JsonNode value = node(key);
            Set<String> picked = new LinkedHashSet<>();
            if (value != null) {
                require(value.isArray() && value.size() <= allowed.size());
                for (JsonNode item : value) {
                    require(item.isString() && allowed.contains(item.asString()));
                    picked.add(item.asString());
                }
            }
            require(!required || !picked.isEmpty());
            if (!picked.isEmpty()) {
                var array = out.putArray(key);
                picked.forEach(array::add);
            }
            return picked;
        }

        long integer(String key, long min, long max, boolean required) {
            JsonNode value = node(key);
            if (value == null) {
                require(!required);
                return 0;
            }
            require(value.isIntegralNumber() && value.canConvertToLong());
            long number = value.longValue();
            require(number >= min && number <= max);
            out.put(key, number);
            return number;
        }

        LocalDate date(String key) {
            String text = text(key, 10, true);
            try {
                LocalDate date = LocalDate.parse(text);
                out.put(key, date.toString());
                return date;
            } catch (DateTimeParseException e) {
                throw invalid();
            }
        }

        String url(String key, boolean required) {
            String text = text(key, 500, required);
            if (!text.isEmpty()) {
                try {
                    URI uri = URI.create(text);
                    require(("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null);
                } catch (IllegalArgumentException e) {
                    throw invalid();
                }
            }
            return text;
        }

        String file(String key, boolean required) {
            String text = text(key, 200, required);
            require(text.isEmpty() || FILE_KEY.matcher(text).matches());
            return text;
        }
    }

    /** 답변에 들어 있는 올린 파일 키(칸 이름 → 키). */
    public static List<String> fileFields() {
        return List.of("couponFileKey", "storeFileKey", "logoFileKey");
    }

    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }
}
