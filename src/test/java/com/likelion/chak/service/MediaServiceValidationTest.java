package com.likelion.chak.service;

import com.likelion.chak.domain.MediaPurpose;
import com.likelion.chak.domain.MediaAsset;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MediaPresignRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import com.likelion.chak.repository.MediaAssetRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class MediaServiceValidationTest {

    @Autowired
    private MediaService mediaService;

    @Test
    void rejectsNonImageContentType() {
        UserAccount user = UserAccount.createKakao("media-test-1", "사용자", null, null);

        assertThatThrownBy(() -> mediaService.createUpload(user, new MediaPresignRequest(
                MediaPurpose.CARD,
                "document.pdf",
                "application/pdf",
                1000)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEDIA);
    }

    @Test
    void profileImageIsLimitedToFiveMegabytes() {
        UserAccount user = UserAccount.createKakao("media-test-2", "사용자", null, null);

        assertThatThrownBy(() -> mediaService.createUpload(user, new MediaPresignRequest(
                MediaPurpose.PROFILE,
                "profile.png",
                "image/png",
                5L * 1024 * 1024 + 1)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEDIA);
    }

    @Test
    void completionMovesUploadAwayFromReusableTemporaryKey() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        S3Presigner presigner = mock(S3Presigner.class);
        S3Client s3Client = mock(S3Client.class);
        MediaService service = new MediaService(repository, presigner, s3Client);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "presignExpirationSeconds", 600L);

        UserAccount owner = UserAccount.createKakao("media-owner", "사용자", null, null);
        ReflectionTestUtils.setField(owner, "id", 7L);
        MediaAsset asset = MediaAsset.pending(
                owner, MediaPurpose.CARD, "tmp/users/7/card/upload.webp",
                "upload.webp", "image/webp", 1024L);
        ReflectionTestUtils.setField(asset, "id", 11L);
        when(repository.findForUpdateByIdAndOwnerId(11L, 7L)).thenReturn(Optional.of(asset));
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1024L)
                        .contentType("image/webp")
                        .build());

        service.complete(7L, 11L);

        assertThat(asset.getObjectKey()).isEqualTo("users/7/card/11.webp");
        verify(s3Client).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void completionRecoversWhenFinalObjectExistsAfterPreviousDatabaseRollback() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        S3Presigner presigner = mock(S3Presigner.class);
        S3Client s3Client = mock(S3Client.class);
        MediaService service = new MediaService(repository, presigner, s3Client);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "presignExpirationSeconds", 600L);

        UserAccount owner = UserAccount.createKakao("media-recovery-owner", "사용자", null, null);
        ReflectionTestUtils.setField(owner, "id", 8L);
        MediaAsset asset = MediaAsset.pending(
                owner, MediaPurpose.CARD, "tmp/users/8/card/upload.webp",
                "upload.webp", "image/webp", 2048L);
        ReflectionTestUtils.setField(asset, "id", 12L);
        when(repository.findForUpdateByIdAndOwnerId(12L, 8L)).thenReturn(Optional.of(asset));
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(2048L)
                        .contentType("image/webp")
                        .build());

        service.complete(8L, 12L);

        assertThat(asset.getObjectKey()).isEqualTo("users/8/card/12.webp");
        assertThat(asset.getStatus().name()).isEqualTo("UPLOADED");
    }
}
