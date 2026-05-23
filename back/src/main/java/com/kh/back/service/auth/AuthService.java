package com.kh.back.service.auth;


import com.kh.back.constant.Authority;
import com.kh.back.dto.auth.LoginDto;
import com.kh.back.dto.auth.SignupDto;
import com.kh.back.dto.auth.TokenDto;
import com.kh.back.entity.member.Member;
import com.kh.back.jwt.TokenProvider;
import com.kh.back.repository.member.MemberRepository;
import com.kh.back.service.FirebaseService;
import com.kh.back.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
// 스프링게 조금 더 낫다


@Slf4j  // 로그 정보를 출력하기 위함
@Service    // 스프링 컨테이너에 빈(객체) 등록
@RequiredArgsConstructor    // 생성자 생성
@Transactional  // 여러가지 작업을 하나의 논리적인 단위로 묶어 줌
public class AuthService {
	// 생성자를 통한 의존성 주입, 생상자를 통해서 의존성 주입을 받는 경우 AutoWired 생략
	private final AuthenticationManagerBuilder managerBuilder; // 인증을 담당하는 클래스
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenProvider tokenProvider;
	private final RedisRefreshTokenService redisRefreshTokenService;
	private final RedisTokenBlacklistService redisTokenBlacklistService;
	private  final MemberService memberService;
	private final FirebaseService firebaseService;

	//---------------------------- 중복확인 ---------------------------------------------
	// 회원가입 여부  // 이메일 존재 여부
	public boolean existEmail(String email) {
		return memberRepository.existsByEmail(email);
	}
	// 닉네임 여부 확인
	public boolean existNickName(String nickname) {
		return memberRepository.existsByNickName(nickname);
	}
	// 핸드폰 중복 여부 확인
	public boolean existPhone(String phone) {
		return memberRepository.existsByPhone(phone);
	}
//-----------------------------------회원가입 및 로그인 ----------------------------------------

	// 회원가입
	
//	public String signup(SignupDto signupDto) {
//		try {
//			if (memberRepository.existsByEmail(signupDto.getEmail())) {
//				throw new RuntimeException("이미 가입되어 있는 유저입니다.");
//			}
//
//			// 엔티티 변환 및 저장
//			Member member = signupDto.toEntity(passwordEncoder);
//			memberRepository.save(member);
//			return "성공";
//		} catch (Exception e) {
//			log.error(e.getMessage());
//			return e.getMessage();
//		}
//	}

	public String signup(SignupDto signupDto, MultipartFile profileImage) {
		try {
			// 이메일 중복 확인
			if (memberRepository.existsByEmail(signupDto.getEmail())) {
				throw new RuntimeException("이미 가입되어 있는 유저입니다.");
			}

			// 프로필 이미지 업로드 (파일이 있는 경우에만 처리)
			String imageUrl = null;
			if (profileImage != null && !profileImage.isEmpty()) {
				imageUrl = firebaseService.uploadProfileImage(profileImage);  // Firebase에 업로드하고 URL을 받음
			}
			// SignupDto의 imagePath에 URL 저장
			signupDto.setMemberImg(imageUrl);
			// 엔티티 변환 및 저장
			Member member = signupDto.toEntity(passwordEncoder);
			memberRepository.save(member);
			return "성공";
		} catch (Exception e) {
			log.error("회원가입 실패: {}", e.getMessage());
			return "회원가입 중 오류가 발생했습니다.";
		}
	}



	// member 로그인

	public TokenDto login(LoginDto loginDto) {
		try {
			log.warn("받아온 데이터{}", loginDto);
			Member member = memberRepository.findByEmail(loginDto.getEmail())
					.orElseThrow(() -> new RuntimeException("이메일 또는 비밀번호가 일치하지 않습니다."));

			// 회원의 membership 상태 확인
			if (member.getAuthority() == Authority.REST_USER) {
				// 탈퇴된 회원일 경우 예외 처리
				throw new RuntimeException("탈퇴한 회원입니다.");
			}
			UsernamePasswordAuthenticationToken authenticationToken = loginDto.toAuthentication();
			log.info("authenticationToken : {}", authenticationToken);

			Authentication authentication = managerBuilder.getObject().authenticate(authenticationToken);
			log.info("authentication : {}", authentication);

			TokenDto token = tokenProvider.generateTokenDto(authentication);
			saveRefreshToken(member, token);
			return token;
		} catch (RuntimeException e) {
			// 이미 정의된 메시지가 있는 경우 그대로 전달
			String message = e.getMessage();
			if (message != null && (message.contains("이메일") || message.contains("비밀번호") || message.contains("탈퇴"))) {
				log.error("로그인 실패 : {}", message);
				throw e;
			}
			// 비밀번호 불일치 등 다른 인증 오류
			log.error("로그인 중 에러 발생 : ", e);
			throw new RuntimeException("이메일 또는 비밀번호가 일치하지 않습니다.");
		}
	}

	public TokenDto refreshAccessToken(String refreshToken) {
		try {
			if (!tokenProvider.validateToken(refreshToken)) {
				log.warn("[refreshAccessToken] invalid refresh token");
				return null;
			}

			Long memberId = tokenProvider.getMemberId(refreshToken);
			Member member = memberRepository.findById(memberId)
					.orElseThrow(() -> new IllegalArgumentException("회원 정보가 존재하지 않습니다."));

			if (!redisRefreshTokenService.exists(memberId)) {
				log.warn("[refreshAccessToken] refresh token reuse detected - no stored token. memberId={}", memberId);
				redisRefreshTokenService.deleteAllTokensByMember(memberId);
				throw new RuntimeException("리프레시 토큰 재사용이 감지되었습니다. 다시 로그인해주세요.");
			}

			if (!redisRefreshTokenService.matches(memberId, refreshToken)) {
				log.warn("[refreshAccessToken] refresh token reuse detected - mismatch. memberId={}", memberId);
				redisRefreshTokenService.deleteAllTokensByMember(memberId);
				throw new RuntimeException("리프레시 토큰 재사용이 감지되었습니다. 다시 로그인해주세요.");
			}

			Authentication authentication = tokenProvider.getAuthentication(refreshToken);
			TokenDto rotatedToken = tokenProvider.generateTokenDto(authentication);
			redisRefreshTokenService.deleteRefreshToken(memberId);
			saveRefreshToken(member, rotatedToken);
			log.info("[refreshAccessToken] token rotated. memberId={}", memberId);
			return rotatedToken;
		} catch (RuntimeException e) {
			log.error("[refreshAccessToken] failed: {}", e.getMessage());
			throw e;
		}
	}
	
	public void saveRefreshToken(Member member, TokenDto token) {
		try {
			redisRefreshTokenService.saveRefreshToken(
					member.getMemberId(),
					token.getRefreshToken(),
					token.getRefreshTokenExpiresIn()
			);
		} catch (Exception e) {
			log.error("리프레시 토큰 저장 실패 : {}", e.getMessage());
		}
	}
	
	public void logout(Long memberId, String accessToken) {
		// 1. Redis에서 Refresh Token 삭제
		redisRefreshTokenService.deleteRefreshToken(memberId);
		// 2. Access Token 블랙리스트 등록 (만료 시각까지 TTL 설정)
		long expiresAtMillis = tokenProvider.getExpirationTime(accessToken);
		redisTokenBlacklistService.addToBlacklist(accessToken, expiresAtMillis);
		log.info("[logout] memberId={} 로그아웃 완료", memberId);
	}

	public String getEmailByPhone(String phone) {
		try {
			Member member = memberRepository.findByPhone(phone)
				.orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));
			return member.getEmail();
		}
		catch (Exception e) {
			log.error("전화번호 : {} 를 통해 이메일 찾는 도중 실패 : {}", phone, e.getMessage());
			return null;
		}
	}
}
