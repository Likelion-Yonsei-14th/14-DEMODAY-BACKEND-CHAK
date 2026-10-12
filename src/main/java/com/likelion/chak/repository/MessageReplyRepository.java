package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageReply;
import com.likelion.chak.domain.PersonalDesk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageReplyRepository extends JpaRepository<MessageReply, Long> {

    List<MessageReply> findAllByDeskOrderByCreatedAtDescIdDesc(PersonalDesk desk);
}
