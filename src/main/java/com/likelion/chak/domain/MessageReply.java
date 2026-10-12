package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 책상 주인이 받은 편지에 남기는 일방향 답장. 대상 편지는 MessageReplyTarget으로 연결한다. */
@Getter
@Entity
@Table(name = "message_replies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desk_id", nullable = false)
    private PersonalDesk desk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserAccount ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplyScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_message_id")
    private Message sourceMessage;

    @Column(name = "reply_text", nullable = false, length = 500)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private MessageReply(PersonalDesk desk, UserAccount ownerUser, ReplyScope scope, Message sourceMessage, String text) {
        this.desk = desk;
        this.ownerUser = ownerUser;
        this.scope = scope;
        this.sourceMessage = sourceMessage;
        this.text = text;
    }

    public static MessageReply create(
            PersonalDesk desk, UserAccount ownerUser, ReplyScope scope, Message sourceMessage, String text) {
        return new MessageReply(desk, ownerUser, scope, sourceMessage, text);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
