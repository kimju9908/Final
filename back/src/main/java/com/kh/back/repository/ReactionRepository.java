package com.kh.back.repository;

import com.kh.back.constant.ReactionType;
import com.kh.back.entity.Reaction;
import com.kh.back.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    Optional<Reaction> findByMemberAndPostIdAndContentType(Member member, String postId, String contentType);
    List<Reaction> findByMember(Member member);
    List<Reaction> findByMemberAndReactionType(Member member, ReactionType reactionType);
    List<Reaction> findByMemberAndReactionTypeAndContentType(Member member, ReactionType reactionType, String contentType);
    long countByPostIdAndContentTypeAndReactionType(String postId, String contentType, ReactionType reactionType);
}
