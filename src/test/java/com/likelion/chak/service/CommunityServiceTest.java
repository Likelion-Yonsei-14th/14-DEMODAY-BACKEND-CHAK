package com.likelion.chak.service;

import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.ReactionType;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.ReplyScope;
import com.likelion.chak.domain.ReportStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.CommunityDtos.ReplyCreateRequest;
import com.likelion.chak.dto.CommunityDtos.ReportAction;
import com.likelion.chak.dto.CommunityDtos.ReportResponse;
import com.likelion.chak.dto.CommunityDtos.SentMessageResponse;
import com.likelion.chak.dto.CommunityDtos.SupporterResponse;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponse;
import com.likelion.chak.dto.OwnerMessageResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommunityServiceTest {

    @Autowired private DeskService deskService;
    @Autowired private MessageService messageService;
    @Autowired private CommunityService communityService;
    @Autowired private ReplyService replyService;
    @Autowired private BlockService blockService;
    @Autowired private ReportAdminService reportAdminService;
    @Autowired private GuestIdentityService guestIdentityService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ObjectMapper objectMapper;

    private record Setup(UserAccount owner, PersonalDesk desk) {
    }

    private Setup setup(String kakaoId) {
        UserAccount owner = userAccountRepository.save(UserAccount.createKakao(kakaoId, "주인", null, null));
        deskService.createMyDesk(owner, new DeskCreateRequest(
                "책상", ReadModeType.TIME_CAPSULE, null, Instant.parse("2026-01-01T00:00:00Z"),
                true, MessageVisibility.PUBLIC));
        return new Setup(owner, deskService.findByOwnerId(owner.getId()));
    }

    private CallerIdentity guest() {
        return new CallerIdentity(null, guestIdentityService.resolveOrCreate(null).getGuestIdentity());
    }

    private MessageResponse send(PersonalDesk desk, CallerIdentity guest, String nickname, String visibility) {
        GuestMessageCreateRequest request = objectMapper.readValue("""
                {"nickname": "%s", "kind": "CARD", "visibility": "%s", "schemaVersion": 1,
                 "card": {"pages": [{"id": "p1"}]},
                 "object": {"representationType": "LETTER", "x": 50, "y": 50, "rotation": 0, "scale": 1, "zIndex": 1}}
                """.formatted(nickname, visibility), GuestMessageCreateRequest.class);
        return messageService.createGuestMessage(desk.getSupporterToken(), guest.guest(), request);
    }

    @Test
    void authorSeesSentListAndCanDeleteUnreadPublicMessage() {
        Setup s = setup("community-sent");
        CallerIdentity friend = guest();
        MessageResponse publicMessage = send(s.desk, friend, "친구", "PUBLIC");
        send(s.desk, friend, "친구", "PRIVATE");

        List<SentMessageResponse> sent = communityService.getSentMessages(friend, 0, 20).content();
        assertThat(sent).hasSize(2);
        assertThat(sent).filteredOn(m -> m.messageId().equals(publicMessage.getId()))
                .singleElement().satisfies(m -> {
                    assertThat(m.deletable()).isTrue();
                    assertThat(m.supporterToken()).isEqualTo(s.desk.getSupporterToken());
                });
        assertThat(sent).filteredOn(m -> m.visibility() == MessageVisibility.PRIVATE)
                .singleElement().satisfies(m -> assertThat(m.deletable()).isFalse());

        // 다른 사람은 삭제할 수 없고, 작성자는 삭제할 수 있다.
        assertThatThrownBy(() -> communityService.deleteOwnMessage(guest(), publicMessage.getId()))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
        communityService.deleteOwnMessage(friend, publicMessage.getId());
        assertThat(communityService.getSentMessages(friend, 0, 20).content()).hasSize(1);
        assertThat(messageService.getPublicMessages(s.desk.getSupporterToken())).isEmpty();
    }

    @Test
    void readMessageCannotBeDeletedByAuthor() {
        Setup s = setup("community-read-delete");
        CallerIdentity friend = guest();
        MessageResponse message = send(s.desk, friend, "친구", "PUBLIC");
        OwnerMessageResponse received = messageService.getOwnerMessages(s.owner.getId(), true).get(0);
        messageService.markOwnerMessageRead(s.owner.getId(), received.deliveryId());

        assertThatThrownBy(() -> communityService.deleteOwnMessage(friend, message.getId()))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_DELETABLE);
    }

    @Test
    void reactionsFollowVisibilityRules() {
        Setup s = setup("community-reaction");
        CallerIdentity friend = guest();
        CallerIdentity visitor = guest();
        CallerIdentity ownerIdentity = new CallerIdentity(s.owner, null);
        MessageResponse open = send(s.desk, friend, "친구", "PUBLIC");
        MessageResponse hidden = send(s.desk, friend, "친구", "PRIVATE");

        assertThat(communityService.react(visitor, open.getId(), ReactionType.HEART).myReaction())
                .isEqualTo(ReactionType.HEART);
        communityService.react(visitor, open.getId(), ReactionType.CLAP); // 같은 사람은 하나만 유지(변경)
        assertThat(communityService.myReactions(visitor, List.of(open.getId(), hidden.getId())))
                .containsExactly(java.util.Map.entry(open.getId(), ReactionType.CLAP));

        // 비공개 편지는 책상 주인만 반응할 수 있다.
        assertThatThrownBy(() -> communityService.react(visitor, hidden.getId(), ReactionType.HEART))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
        assertThat(communityService.react(ownerIdentity, hidden.getId(), ReactionType.TEARY).myReaction())
                .isEqualTo(ReactionType.TEARY);

        communityService.removeReaction(visitor, open.getId());
        assertThat(communityService.myReactions(visitor, List.of(open.getId()))).isEmpty();
    }

    @Test
    void reportIsIdempotentAndAdminCanRemoveMessage() {
        Setup s = setup("community-report");
        CallerIdentity friend = guest();
        CallerIdentity visitor = guest();
        MessageResponse message = send(s.desk, friend, "친구", "PUBLIC");

        communityService.report(visitor, message.getId(), "욕설");
        communityService.report(visitor, message.getId(), "욕설");
        List<ReportResponse> open = reportAdminService.list(ReportStatus.OPEN, 0, 20).content();
        assertThat(open).hasSize(1);

        reportAdminService.act(open.get(0).id(), ReportAction.REMOVE_MESSAGE);
        assertThat(reportAdminService.list(ReportStatus.OPEN, 0, 20).content()).isEmpty();
        assertThat(messageService.getPublicMessages(s.desk.getSupporterToken())).isEmpty();
        assertThat(reportAdminService.list(ReportStatus.RESOLVED, 0, 20).content())
                .singleElement().satisfies(r -> assertThat(r.messageStatus()).isEqualTo(MessageStatus.DELETED));
    }

    @Test
    void ownerRepliesOnceAndAuthorSeesTheReply() {
        Setup s = setup("community-reply");
        CallerIdentity friend = guest();
        CallerIdentity other = guest();
        MessageResponse mine = send(s.desk, friend, "친구", "PUBLIC");
        MessageResponse notMine = send(s.desk, other, "다른친구", "PUBLIC");

        var reply = replyService.create(s.owner.getId(),
                new ReplyCreateRequest(ReplyScope.SINGLE, mine.getId(), List.of(mine.getId()), "고마워!"));
        assertThat(reply.targetMessageIds()).containsExactly(mine.getId());

        assertThatThrownBy(() -> replyService.create(s.owner.getId(),
                new ReplyCreateRequest(ReplyScope.DAILY, null, List.of(mine.getId(), notMine.getId()), "또")))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.REPLY_ALREADY_EXISTS);

        assertThat(replyService.listForOwner(s.owner.getId())).hasSize(1);
        var seen = replyService.listForAuthor(friend);
        assertThat(seen).singleElement().satisfies(r -> {
            assertThat(r.text()).isEqualTo("고마워!");
            assertThat(r.ownerName()).isEqualTo("책상");
            assertThat(r.targetSenderNames()).containsExactly("친구");
        });
        assertThat(replyService.listForAuthor(other)).isEmpty();

        // 다른 책상의 편지에는 답장할 수 없다.
        Setup foreign = setup("community-reply-foreign");
        assertThatThrownBy(() -> replyService.create(foreign.owner.getId(),
                new ReplyCreateRequest(ReplyScope.SINGLE, null, List.of(notMine.getId()), "x")))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REPLY);
    }

    @Test
    void blockedAuthorDisappearsFromOwnerAndPublicListsUntilUnblocked() {
        Setup s = setup("community-block");
        CallerIdentity annoying = guest();
        CallerIdentity kind = guest();
        send(s.desk, annoying, "불편한친구", "PUBLIC");
        send(s.desk, annoying, "불편한친구", "PUBLIC");
        send(s.desk, kind, "착한친구", "PUBLIC");

        List<SupporterResponse> supporters = blockService.supporters(s.owner.getId());
        assertThat(supporters).extracting(SupporterResponse::displayName).containsExactlyInAnyOrder("불편한친구", "착한친구");
        SupporterResponse target = supporters.stream().filter(x -> x.displayName().equals("불편한친구")).findFirst().orElseThrow();
        assertThat(target.messageCount()).isEqualTo(2);

        var block = blockService.block(s.owner.getId(), target.deliveryId());
        assertThat(blockService.block(s.owner.getId(), target.deliveryId()).id()).isEqualTo(block.id()); // 멱등
        assertThat(messageService.getOwnerMessages(s.owner.getId(), true)).hasSize(1);
        assertThat(messageService.getPublicMessages(s.desk.getSupporterToken())).hasSize(1);
        assertThat(blockService.supporters(s.owner.getId()))
                .filteredOn(SupporterResponse::blocked).singleElement()
                .satisfies(x -> assertThat(x.blockId()).isEqualTo(block.id()));

        blockService.unblock(s.owner.getId(), block.id());
        assertThat(messageService.getOwnerMessages(s.owner.getId(), true)).hasSize(3);
        assertThat(messageService.getPublicMessages(s.desk.getSupporterToken())).hasSize(3);
    }
}
