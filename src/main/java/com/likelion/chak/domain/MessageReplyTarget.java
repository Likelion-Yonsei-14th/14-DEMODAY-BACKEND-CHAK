package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "message_reply_targets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReplyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reply_id", nullable = false)
    private MessageReply reply;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private Message message;

    private MessageReplyTarget(MessageReply reply, Message message) {
        this.reply = reply;
        this.message = message;
    }

    public static MessageReplyTarget create(MessageReply reply, Message message) {
        return new MessageReplyTarget(reply, message);
    }
}
