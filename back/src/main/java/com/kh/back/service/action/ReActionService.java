package com.kh.back.service.action;

import com.kh.back.constant.ReactionType;
import com.kh.back.entity.Reaction;
import com.kh.back.entity.member.Member;
import com.kh.back.repository.ReactionRepository;
import com.kh.back.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReActionService {

    private final MemberRepository memberRepository;
    private final ReactionRepository reactionRepository;

    @Transactional(readOnly = true)
    public ReactionType getCurrentReaction(Authentication authentication, String postId, String contentType) {
        if (authentication == null) {
            return ReactionType.NONE;
        }

        Member member = getMember(authentication);
        return reactionRepository.findByMemberAndPostIdAndContentType(member, postId, contentType)
                .map(Reaction::getReactionType)
                .orElse(ReactionType.NONE);
    }

    @Transactional
    public ReactionType syncReaction(Authentication authentication, String postId, String contentType, ReactionType nextReaction) {
        Member member = getMember(authentication);
        Optional<Reaction> existingReaction = reactionRepository.findByMemberAndPostIdAndContentType(member, postId, contentType);

        if (nextReaction == ReactionType.NONE) {
            existingReaction.ifPresent(reactionRepository::delete);
            log.info("[syncReaction] Deleted reaction memberId={}, postId={}, type={}", member.getMemberId(), postId, contentType);
            return ReactionType.NONE;
        }

        Reaction reaction = existingReaction
                .map(existing -> {
                    existing.setReactionType(nextReaction);
                    return existing;
                })
                .orElseGet(() -> new Reaction(member, postId, contentType, nextReaction));

        reactionRepository.save(reaction);
        log.info("[syncReaction] Saved reaction memberId={}, postId={}, type={}, reaction={}",
                member.getMemberId(), postId, contentType, nextReaction);
        return nextReaction;
    }

    private Member getMember(Authentication authentication) {
        if (authentication == null) {
            throw new InsufficientAuthenticationException("좋아요 기능은 로그인 후 사용할 수 있습니다.");
        }

        Long memberId = Long.parseLong(authentication.getName());
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보가 존재하지 않습니다."));
    }
}
