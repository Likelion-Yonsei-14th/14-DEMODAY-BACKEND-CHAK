package com.likelion.chak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tools.jackson.core.StreamReadConstraints;

@SpringBootApplication
public class ChakApplication {

    /** 이 프로세스가 읽는 JSON의 상한(본문 크기 필터와 함께 큰·깊은 JSON 트리로 힙을 터뜨리는 요청을 막는다). */
    public static final StreamReadConstraints JSON_READ_CONSTRAINTS = StreamReadConstraints.builder()
            .maxDocumentLength(4_000_000L)
            .maxStringLength(2_000_000)
            .maxNestingDepth(64)
            .maxTokenCount(200_000L)
            .build();

    static {
        // JsonFactory가 만들어지기 전에 적용되어야 한다. 테스트도 이 클래스를 먼저 읽으므로 여기 둔다.
        StreamReadConstraints.overrideDefaultStreamReadConstraints(JSON_READ_CONSTRAINTS);
    }

    public static void main(String[] args) {
        SpringApplication.run(ChakApplication.class, args);
    }
}
