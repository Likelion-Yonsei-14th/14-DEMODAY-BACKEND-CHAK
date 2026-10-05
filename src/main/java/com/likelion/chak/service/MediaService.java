package com.likelion.chak.service;

import com.likelion.chak.domain.MediaAsset;
import com.likelion.chak.domain.MediaPurpose;
import com.likelion.chak.domain.MediaStatus;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MediaAssetResponse;
import com.likelion.chak.dto.MediaDownloadResponse;
import com.likelion.chak.dto.MediaPresignRequest;
import com.likelion.chak.dto.MediaPresignResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final long MAX_PROFILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_CARD_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final MediaAssetRepository mediaAssetRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.presign-expiration-seconds}")
    private long presignExpirationSeconds;

    @Transactional
    public MediaPresignResponse createUpload(UserAccount owner, MediaPresignRequest request) {
        validateConfiguration();
        validateRequest(request);

        String objectKey = createTemporaryObjectKey(owner.getId(), request.purpose(), request.contentType());
        MediaAsset asset = mediaAssetRepository.save(MediaAsset.pending(
                owner,
                request.purpose(),
                objectKey,
                request.originalFileName().trim(),
                request.contentType(),
                request.size()));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(request.contentType())
                .contentLength(request.size())
                .build();
        Duration duration = Duration.ofSeconds(presignExpirationSeconds);
        String uploadUrl;
        try {
            uploadUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .putObjectRequest(putObjectRequest)
                            .build())
                    .url()
                    .toExternalForm();
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }

        return new MediaPresignResponse(
                asset.getId(),
                uploadUrl,
                "PUT",
                Map.of("Content-Type", request.contentType()),
                Instant.now().plus(duration));
    }

    @Transactional
    public MediaAssetResponse complete(Long ownerId, Long assetId) {
        validateConfiguration();
        MediaAsset asset = findOwnedAssetForUpdate(ownerId, assetId);
        if (asset.getStatus() == MediaStatus.UPLOADED) {
            return MediaAssetResponse.from(asset);
        }

        String temporaryKey = asset.getObjectKey();
        String finalKey = createFinalObjectKey(asset);
        HeadObjectResponse temporaryObject = headObject(temporaryKey);
        if (temporaryObject == null) {
            HeadObjectResponse recoveredFinalObject = headObject(finalKey);
            if (recoveredFinalObject == null) {
                throw new CustomException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND);
            }
            validateUploadedObject(asset, recoveredFinalObject);
            asset.complete(finalKey, Instant.now());
            return MediaAssetResponse.from(asset);
        }
        validateUploadedObject(asset, temporaryObject);

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(temporaryKey)
                    .destinationBucket(bucket)
                    .destinationKey(finalKey)
                    .contentType(asset.getContentType())
                    .metadataDirective("REPLACE")
                    .build());
            asset.complete(finalKey, Instant.now());
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(temporaryKey)
                    .build());
        } catch (SdkException e) {
            log.warn("Temporary media cleanup failed for asset {}", asset.getId(), e);
        }
        return MediaAssetResponse.from(asset);
    }

    @Transactional(readOnly = true)
    public MediaDownloadResponse createDownload(Long ownerId, Long assetId) {
        validateConfiguration();
        MediaAsset asset = findOwnedAsset(ownerId, assetId);
        if (asset.getStatus() != MediaStatus.UPLOADED) {
            throw new CustomException(ErrorCode.MEDIA_UPLOAD_NOT_FOUND);
        }

        Duration duration = Duration.ofSeconds(presignExpirationSeconds);
        String downloadUrl;
        try {
            downloadUrl = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(asset.getObjectKey())
                                    .build())
                            .build())
                    .url()
                    .toExternalForm();
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
        return new MediaDownloadResponse(downloadUrl, Instant.now().plus(duration));
    }

    private MediaAsset findOwnedAsset(Long ownerId, Long assetId) {
        return mediaAssetRepository.findByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaAsset findOwnedAssetForUpdate(Long ownerId, Long assetId) {
        return mediaAssetRepository.findForUpdateByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private HeadObjectResponse headObject(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.STORAGE_API_ERROR);
        }
    }

    private void validateUploadedObject(MediaAsset asset, HeadObjectResponse head) {
        if (head.contentLength() != asset.getExpectedSize()
                || head.contentType() == null
                || !head.contentType().equalsIgnoreCase(asset.getContentType())) {
            throw new CustomException(ErrorCode.INVALID_MEDIA);
        }
    }

    private void validateConfiguration() {
        if (bucket.isBlank()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }
    }

    private void validateRequest(MediaPresignRequest request) {
        long maximum = request.purpose() == MediaPurpose.PROFILE ? MAX_PROFILE_SIZE : MAX_CARD_SIZE;
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType()) || request.size() > maximum) {
            throw new CustomException(ErrorCode.INVALID_MEDIA);
        }
    }

    private String createTemporaryObjectKey(Long ownerId, MediaPurpose purpose, String contentType) {
        return "tmp/users/%d/%s/%s.%s".formatted(
                ownerId,
                purpose.name().toLowerCase(),
                UUID.randomUUID(),
                extension(contentType));
    }

    private String createFinalObjectKey(MediaAsset asset) {
        return "users/%d/%s/%d.%s".formatted(
                asset.getOwner().getId(),
                asset.getPurpose().name().toLowerCase(),
                asset.getId(),
                extension(asset.getContentType()));
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new CustomException(ErrorCode.INVALID_MEDIA);
        };
    }

}
