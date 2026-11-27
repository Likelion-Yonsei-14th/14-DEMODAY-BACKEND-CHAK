package com.likelion.chak.service;

import com.likelion.chak.alert.AlertMessage;
import com.likelion.chak.alert.AlertSanitizer;
import com.likelion.chak.alert.ErrorAlertService;
import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.BenefitSubmission;
import com.likelion.chak.domain.PromotionInquiry;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.BenefitDtos.FileLink;
import com.likelion.chak.dto.BenefitDtos.SubmissionDetail;
import com.likelion.chak.dto.BenefitDtos.SubmissionSummary;
import com.likelion.chak.dto.BenefitDtos.SubmitRequest;
import com.likelion.chak.dto.BenefitDtos.UploadPurpose;
import com.likelion.chak.dto.BenefitDtos.UploadRequest;
import com.likelion.chak.dto.BenefitDtos.UploadResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.BenefitSubmissionRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 혜택 시안 신청 접수와 운영자 확인. 접수하면 운영자 디스코드에 알리되 연락처는 알림에 싣지 않는다. */
@Service
@RequiredArgsConstructor
public class BenefitSubmissionService {

    static final long MAX_UPLOAD_SIZE = 20L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("ai", "application/octet-stream"), Map.entry("eps", "application/octet-stream"),
            Map.entry("zip", "application/zip"), Map.entry("csv", "text/csv"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"));
    private static final Set<String> LOGO_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg", "webp", "pdf", "ai", "eps", "zip");
    private static final Set<String> SHEET_EXTENSIONS = Set.of("csv", "xlsx", "xls");
    /** 브라우저가 문서로 열어도 스크립트가 돌지 않는 그림 형식. 이 밖의 파일(SVG 포함)은 내려받기 전용 형식으로 내준다. */
    private static final Set<String> RASTER_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    /** 신청서에 연결되지 않은 업로드를 1일 만에 지우는 S3 수명 주기 규칙이 보는 태그. 신청서를 받으면 태그를 지운다. */
    static final String PENDING_TAG_KEY = "chak-pending";
    static final String PENDING_TAG = PENDING_TAG_KEY + "=1";

    private final BenefitSubmissionRepository repository;
    private final BenefitAnswerValidator validator;
    private final UserAccountRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ErrorAlertService alertService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.presign-expiration-seconds}")
    private long presignExpirationSeconds;

    /**
     * 켜면 업로드에 대기 태그를 달아(서명된 x-amz-tagging 헤더) 신청서에 연결되지 않은 파일이 수명 주기 규칙으로 1일 뒤 지워진다.
     * 서버 역할에 s3:PutObjectTagging·s3:DeleteObjectTagging 권한과 태그 기반 수명 주기 규칙(chak-stack.yaml)이 먼저 있어야 한다.
     */
    @Value("${chak.promotion.pending-upload-tag:false}")
    private boolean pendingUploadTag;

    /** 올릴 파일 한 개의 주소를 발급한다. 파일은 신청서를 보낼 때 키로 연결된다. */
    public UploadResponse createUpload(UploadRequest request) {
        if (bucket.isBlank()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }
        String extension = extensionOf(request.fileName());
        Set<String> allowed = request.purpose() == UploadPurpose.LOGO ? LOGO_EXTENSIONS : SHEET_EXTENSIONS;
        if (!allowed.contains(extension) || request.size() > MAX_UPLOAD_SIZE) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
        String contentType = CONTENT_TYPES.get(extension);
        String key = BenefitAnswerValidator.FILE_KEY_PREFIX + UUID.randomUUID() + "/" + safeName(request.fileName(), extension);
        Duration duration = Duration.ofSeconds(presignExpirationSeconds);
        try {
            String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .putObjectRequest(PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .contentType(contentType)
                                    .contentLength(request.size())
                                    .tagging(pendingUploadTag ? PENDING_TAG : null)
                                    .build())
                            .build())
                    .url().toExternalForm();
            Map<String, String> headers = pendingUploadTag
                    ? Map.of("Content-Type", contentType, "x-amz-tagging", PENDING_TAG)
                    : Map.of("Content-Type", contentType);
            return new UploadResponse(key, url, "PUT", headers, Instant.now().plus(duration));
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
    }

    /**
     * 신청을 저장한다. 자동 입력(숨은 칸이 채워짐)은 저장하지 않고 성공으로 돌려 스팸 프로그램에 단서를 주지 않는다.
     * 같은 업체가 고쳐서 다시 보낼 수 있어 중복은 막지 않고, 보내는 횟수는 컨트롤러의 접속자별 제한으로 줄인다.
     */
    @Transactional
    public void submit(SubmitRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        ObjectNode answers = validator.validate(request.answers());
        for (String field : BenefitAnswerValidator.fileFields()) {
            String key = answers.path(field).asString("");
            if (!key.isEmpty() && !objectExists(key)) {
                throw new CustomException(ErrorCode.INVALID_BENEFIT_ANSWER);
            }
        }
        for (String field : BenefitAnswerValidator.fileFields()) {
            String key = answers.path(field).asString("");
            if (!key.isEmpty()) {
                keepUpload(key);
            }
        }
        String email = answers.path("email").asString().toLowerCase(Locale.ROOT);
        answers.put("email", email);
        BenefitSubmission saved = repository.save(new BenefitSubmission(
                answers.path("brand").asString(),
                answers.path("category").asString(),
                answers.path("contactName").asString(),
                email,
                answers.path("phone").asString(),
                answers.path("headlineRaw").asString(),
                answers.toString()));

        alertService.alert(AlertMessage.builder("문의", "새 혜택 시안 신청", "benefit-submission|" + saved.getId(),
                        AlertMessage.Severity.WARN)
                .description("어드민 > 혜택 시안 신청에서 확인하세요.")
                .field("브랜드", AlertSanitizer.clean(saved.getBrand(), 100))
                .field("업종", saved.getCategory())
                .build());
    }

    @Transactional(readOnly = true)
    public SliceResponse<SubmissionSummary> list(PromotionInquiry.Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Slice<BenefitSubmission> slice = status == null
                ? repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size))
                : repository.findAllByStatusOrderByCreatedAtDescIdDesc(status, PageRequest.of(page, size));
        return SliceResponse.from(slice, slice.getContent().stream().map(SubmissionSummary::from).toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detail(Long id) {
        BenefitSubmission submission = find(id);
        JsonNode answers = objectMapper.readTree(submission.getAnswers());
        List<FileLink> files = new ArrayList<>();
        for (String field : BenefitAnswerValidator.fileFields()) {
            String key = answers.path(field).asString("");
            if (!key.isEmpty()) {
                files.add(new FileLink(field, key.substring(key.lastIndexOf('/') + 1), downloadUrl(key)));
            }
        }
        return new SubmissionDetail(SubmissionSummary.from(submission), answers, files);
    }

    @Transactional
    public SubmissionSummary changeStatus(Long adminId, Long id, PromotionInquiry.Status status) {
        UserAccount admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        BenefitSubmission submission = find(id);
        submission.changeStatus(status, Instant.now());
        auditLogRepository.save(new AdminAuditLog(admin, "BENEFIT_SUBMISSION_STATUS", "BENEFIT_SUBMISSION", id, status.name()));
        return SubmissionSummary.from(submission);
    }

    private BenefitSubmission find(Long id) {
        return repository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.BENEFIT_NOT_FOUND));
    }

    /** 저장소를 쓰지 않는 환경(개발·시험)에서는 파일 존재를 확인하지 않는다. */
    private boolean objectExists(String key) {
        if (bucket.isBlank()) {
            return true;
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
    }

    /** 신청서에 연결된 파일은 대기 태그를 지워 1일 수명 주기 규칙에서 빼낸다. */
    private void keepUpload(String key) {
        if (!pendingUploadTag || bucket.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObjectTagging(DeleteObjectTaggingRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
    }

    /**
     * 누구나 올린 파일을 운영자가 여는 주소. 항상 내려받기(attachment)로 내주고, 래스터 그림이 아니면 형식도
     * application/octet-stream으로 바꿔 SVG·HTML 같은 파일이 버킷 출처에서 문서로 열려 스크립트가 도는 것을 막는다.
     * 래스터 그림은 원래 형식을 유지해 어드민 화면의 &lt;img&gt; 미리보기는 그대로 된다.
     */
    private String downloadUrl(String key) {
        if (bucket.isBlank()) {
            return "";
        }
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        String extension = extensionOf(fileName);
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentDisposition("attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        if (!RASTER_EXTENSIONS.contains(extension)) {
            get.responseContentType("application/octet-stream");
        }
        try {
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(presignExpirationSeconds))
                            .getObjectRequest(get.build())
                            .build())
                    .url().toExternalForm();
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 키에 한글·공백이 들어가지 않게 파일 이름을 영숫자로 줄인다. 확장자는 유지한다. */
    static String safeName(String fileName, String extension) {
        String stem = fileName.substring(0, Math.max(0, fileName.length() - extension.length() - 1))
                .replaceAll("[^A-Za-z0-9_-]", "_");
        if (stem.length() > 60) {
            stem = stem.substring(0, 60);
        }
        return (stem.isEmpty() ? "file" : stem) + "." + extension;
    }
}
