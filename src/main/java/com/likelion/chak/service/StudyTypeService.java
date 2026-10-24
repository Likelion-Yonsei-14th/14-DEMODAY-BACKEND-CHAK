package com.likelion.chak.service;

import com.likelion.chak.domain.StudyTypeResult;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.StudyTypeShare;
import com.likelion.chak.dto.MinigameDtos.StudyTypeStats;
import com.likelion.chak.repository.StudyTypeResultRepository;
import com.likelion.chak.repository.StudyTypeResultRepository.CodeCount;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 나의 수험생 유형은?: 결과 유형별 사람 수. 회원은 한 줄만 남기고, 다시 하면 유형이 바뀐다. */
@Service
@RequiredArgsConstructor
public class StudyTypeService {

    public static final List<String> CODES = List.of("PSA", "PSN", "PFA", "PFN", "LSA", "LSN", "LFA", "LFN");

    private final StudyTypeResultRepository repository;

    @Transactional
    public StudyTypeStats record(String code, UserAccount user) {
        if (user == null) {
            repository.save(new StudyTypeResult(code, null));
        } else {
            StudyTypeResult mine = repository.findByUserId(user.getId()).orElse(null);
            if (mine != null) {
                mine.changeCode(code);
            } else {
                try {
                    repository.saveAndFlush(new StudyTypeResult(code, user));
                } catch (DataIntegrityViolationException e) {
                    // 같은 회원이 동시에 두 번 보냈다: 먼저 들어간 줄의 유형을 최신 값으로 바꾼다.
                    repository.findByUserId(user.getId()).ifPresent(row -> row.changeCode(code));
                }
            }
        }
        return stats(user);
    }

    @Transactional(readOnly = true)
    public StudyTypeStats stats(UserAccount viewer) {
        Map<String, Long> counts = new HashMap<>();
        for (CodeCount row : repository.countByCode()) {
            counts.put(row.getCode(), row.getCount());
        }
        long total = CODES.stream().mapToLong(c -> counts.getOrDefault(c, 0L)).sum();
        List<StudyTypeShare> shares = new ArrayList<>();
        for (String code : CODES) {
            long count = counts.getOrDefault(code, 0L);
            double percent = total == 0 ? 0 : Math.round(count * 1000.0 / total) / 10.0;
            shares.add(new StudyTypeShare(code, count, percent));
        }
        String mine = viewer == null ? null : repository.findByUserId(viewer.getId()).map(StudyTypeResult::getCode).orElse(null);
        return new StudyTypeStats(total, shares, mine);
    }
}
