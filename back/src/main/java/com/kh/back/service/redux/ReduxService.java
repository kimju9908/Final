package com.kh.back.service.redux;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.constant.ReactionType;
import com.kh.back.constant.Authority;
import com.kh.back.dto.member.res.MemberPublicResDto;
import com.kh.back.dto.member.res.ReduxResDto;
import com.kh.back.entity.Reaction;
import com.kh.back.entity.member.Member;
import com.kh.back.repository.ReactionRepository;
import com.kh.back.repository.member.MemberRepository;
import com.kh.back.service.PurchaseService;
import com.kh.back.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReduxService {
	private final MemberRepository memberRepository;
	private final ObjectMapper objectMapper;
	private final MemberService memberService;
	private final PurchaseService purchaseService;
	@Autowired
	private ReactionRepository reactionRepository;

	public MemberPublicResDto getMemberPublicResDto(Long id) {
		Member member = memberRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("해당 사용자가 없습니다."));
		return objectMapper.convertValue(member, MemberPublicResDto.class);
	}

	// ReduxService.java
	public ReduxResDto getMyInfo(Authentication auth) {
		try {
			// 인증된 사용자 정보를 가져옴
			Member member = memberService.convertAuthToEntity(auth);
			if (member == null || member.getAuthority() == Authority.REST_USER) {
				return null;
			}

			// Reaction 데이터를 가져오기 위한 로직
			List<Reaction> reactions = reactionRepository.findByMember(member);

			// 좋아요 및 신고한 레시피 ID 리스트 만들기
			Set<String> likedRecipes = reactions.stream()
					.filter(reaction -> reaction.getReactionType() == ReactionType.LIKE)
					.map(reaction -> reaction.getContentType() + ":" + reaction.getPostId())
					.collect(Collectors.toSet());

			Set<String> dislikedRecipes = reactions.stream()
					.filter(reaction -> reaction.getReactionType() == ReactionType.DISLIKE)
					.map(reaction -> reaction.getContentType() + ":" + reaction.getPostId())
					.collect(Collectors.toSet());

			// ReduxResDto 생성
			ReduxResDto reduxResDto = new ReduxResDto();
			reduxResDto.setId(member.getMemberId());
			reduxResDto.setEmail(member.getEmail());
			reduxResDto.setNickname(member.getNickName());
			reduxResDto.setRole(member.getAuthority());
			reduxResDto.setLikedRecipes(likedRecipes);
			reduxResDto.setDislikedRecipes(dislikedRecipes);

			// ★ 프리미엄 여부를 설정
			// 예: member 객체에 프리미엄 여부를 판단하는 메서드가 있거나,
			// PurchaseService를 호출하여 구매 기록이 있는지 확인하는 로직을 추가합니다.
			// 아래는 예시입니다.
			boolean premium = purchaseService.isMemberPremium(member.getMemberId());
			reduxResDto.setPremium(premium); // ReduxResDto에 premium 필드를 추가해야 함

			return reduxResDto;
		} catch (Exception e) {
			log.error(e.getMessage());
			return null;
		}
	}



}
