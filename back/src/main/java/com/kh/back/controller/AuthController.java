package com.kh.back.controller;

import com.kh.back.dto.auth.LoginDto;
import com.kh.back.dto.auth.SignupDto;
import com.kh.back.dto.auth.TokenDto;
import com.kh.back.dto.auth.request.EmailRequestDto;
import com.kh.back.dto.auth.request.EmailTokenVerificationReqDto;
import com.kh.back.dto.auth.request.PasswordChangeReqDto;
import com.kh.back.dto.auth.request.PhoneRequestDto;
import com.kh.back.dto.auth.request.RefreshTokenReqDto;
import com.kh.back.dto.auth.request.SmsTokenVerificationReqDto;
import com.kh.back.service.auth.AuthService;
import com.kh.back.service.auth.EmailService;
import com.kh.back.service.auth.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // 회원가입 폼에서 자주 호출되는 중복 확인 조회 API
    @GetMapping("/emails/exists")
    public ResponseEntity<Boolean> checkEmailExists(@RequestParam String email) {
        boolean exists = authService.existEmail(email);
        log.info("[checkEmailExists] email={}, exists={}", email, exists);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/nicknames/exists")
    public ResponseEntity<Boolean> checkNicknameExists(@RequestParam String nickname) {
        boolean exists = authService.existNickName(nickname);
        log.info("[checkNicknameExists] nickname={}, exists={}", nickname, exists);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/phones/exists")
    public ResponseEntity<Boolean> checkPhoneExists(@RequestParam String phone) {
        boolean exists = authService.existPhone(phone);
        log.info("[checkPhoneExists] phone={}, exists={}", phone, exists);
        return ResponseEntity.ok(exists);
    }

    // 회원 등록은 인증 도메인 아래 registrations 자원 생성으로 정리
    @PostMapping("/registrations")
    public ResponseEntity<String> registerMember(
            @ModelAttribute SignupDto signupDto,
            @RequestParam(required = false) MultipartFile profileImage) {
        String result = authService.signup(signupDto, profileImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 비밀번호 재설정용 이메일 토큰 발급 요청
    @PostMapping("/password-reset-tokens")
    public ResponseEntity<Boolean> createPasswordResetToken(@RequestBody EmailRequestDto request) {
        log.info("[createPasswordResetToken] email={}", request.getEmail());
        boolean success = emailService.sendPasswordResetToken(request.getEmail());
        return ResponseEntity.ok(success);
    }

    // 이메일 인증 토큰 검증
    @PostMapping("/email-verifications")
    public ResponseEntity<Boolean> verifyEmailToken(@RequestBody EmailTokenVerificationReqDto request) {
        boolean isValid = emailService.verifyEmailToken(request.getEmail(), request.getInputToken());
        return ResponseEntity.ok(isValid);
    }

    // GET 요청으로 상태를 바꾸던 SMS 발송을 POST로 정리
    @PostMapping("/sms-verification-codes")
    public ResponseEntity<String> createSmsVerificationCode(@RequestBody PhoneRequestDto request) {
        String result = smsService.sendVerificationCode(request.getPhone());
        log.info("[createSmsVerificationCode] phone={}, result={}", request.getPhone(), result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sms-verifications")
    public ResponseEntity<Boolean> verifySmsCode(@RequestBody SmsTokenVerificationReqDto request) {
        boolean isValid = smsService.verifySmsCode(request.getPhone(), request.getInputToken());
        return ResponseEntity.ok(isValid);
    }

    // phone 기반 email 조회는 검색 조건이므로 query string 사용
    @GetMapping("/accounts/email")
    public ResponseEntity<String> findEmailByPhone(@RequestParam String phone) {
        String email = authService.getEmailByPhone(phone);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(email);
    }

    // 로그인은 토큰 자원 생성으로 표현
    @PostMapping("/tokens")
    public ResponseEntity<?> createToken(@RequestBody LoginDto loginDto) {
        try {
            TokenDto tokenDto = authService.login(loginDto);
            log.info("[createToken] login success for email={}", loginDto.getEmail());
            return ResponseEntity.ok(tokenDto);
        } catch (RuntimeException e) {
            log.error("[createToken] login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/tokens/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenReqDto request) {
        try {
            TokenDto tokenDto = authService.refreshAccessToken(request.getRefreshToken());
            if (tokenDto == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(tokenDto);
        } catch (RuntimeException e) {
            log.error("[refreshToken] failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PatchMapping("/password")
    public ResponseEntity<Boolean> changePassword(@RequestBody PasswordChangeReqDto request) {
        boolean success = emailService.changePassword(request.getPassword(), passwordEncoder);
        return ResponseEntity.ok(success);
    }
}









