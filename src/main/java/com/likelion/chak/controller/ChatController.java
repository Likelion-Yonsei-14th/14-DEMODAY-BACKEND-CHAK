package com.likelion.chak.controller;

import com.likelion.chak.alert.ChatRateLimiter;
import com.likelion.chak.dto.ChatDtos.ChatRequest;
import com.likelion.chak.dto.ChatDtos.ChatResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수능 도우미 챗봇. 로그인 없이 쓰고, 접속자별·전체 횟수 제한이 있다. */
@RestController
@RequestMapping("/api/public/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(ClientIp.of(http))) throw new CustomException(ErrorCode.TOO_MANY_CHATS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chatService.answer(request.messages()));
    }
}
