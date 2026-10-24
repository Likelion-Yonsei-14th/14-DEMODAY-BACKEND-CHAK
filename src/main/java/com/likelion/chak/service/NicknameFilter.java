package com.likelion.chak.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 닉네임에 욕설·성적·혐오·자해·운영진 사칭 단어가 들어 있는지 본다.
 * 공백·숫자·기호를 끼워 넣거나(시1발, f.u.c.k) 영문 대신 숫자를 쓰는(sh1t) 흔한 우회를 걸러 낸다.
 * 단어 목록은 {@code moderation/*.txt}에 있어 코드를 고치지 않고 늘릴 수 있다.
 * 완벽하지 않으므로 운영자가 신고로 들어온 닉네임을 따로 지울 수 있어야 한다.
 */
@Component
public class NicknameFilter {

    private final List<String> blocked;
    private final List<String> allowed;

    public NicknameFilter() {
        this.blocked = load("moderation/blocked-nickname-words.txt");
        this.allowed = load("moderation/nickname-allowed-words.txt");
        if (blocked.isEmpty()) {
            throw new IllegalStateException("닉네임 금칙어 목록이 비어 있습니다.");
        }
    }

    NicknameFilter(List<String> blocked, List<String> allowed) {
        this.blocked = blocked.stream().map(NicknameFilter::normalize).filter(w -> !w.isEmpty()).toList();
        this.allowed = allowed.stream().map(NicknameFilter::normalize).filter(w -> !w.isEmpty()).toList();
    }

    public boolean isAllowed(String nickname) {
        if (nickname == null) {
            return false;
        }
        return clean(normalize(nickname)) && clean(letters(nickname));
    }

    private boolean clean(String text) {
        for (String word : allowed) {
            text = text.replace(word, "");
        }
        for (String word : blocked) {
            if (text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** 숫자와 기호를 영문으로 바꾸지 않고 그냥 지운 모양. 한글 사이에 낀 숫자(시1발)를 잡는다. */
    private static String letters(String raw) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '가' && c <= '힣')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** 소문자로 바꾸고 영문 대신 쓰는 숫자·기호를 영문으로 되돌린 뒤, 한글·영문 말고는 모두 지운다. */
    static String normalize(String raw) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '0' -> out.append('o');
                case '1', '!' -> out.append('i');
                case '3' -> out.append('e');
                case '4', '@' -> out.append('a');
                case '5', '$' -> out.append('s');
                default -> {
                    if ((c >= 'a' && c <= 'z') || (c >= '가' && c <= '힣')) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static List<String> load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> words = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    String normalized = normalize(word);
                    if (!normalized.isEmpty()) {
                        words.add(normalized);
                    }
                }
            }
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException("금칙어 목록을 읽지 못했습니다: " + path, e);
        }
    }
}
