package com.kh.back.entity.member;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.kh.back.constant.Authority;
import com.kh.back.entity.CustomStyle;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id // 해당 필드를 기본키로 지정
    @Column(name = "member_id")
    @GeneratedValue(strategy = GenerationType.AUTO) //JPA 가 자동으로 생성 전략을 정함
    private Long memberId; // Primary Key
    // nullable=false : null 값이 올 수 없다는 제약 조건
    // length = 50 : 최대 길이(바이트)
    @Column(unique = true)
    private String userId; // 고유 사용자 ID (소셜 또는 직접 가입)
    @Column(name = "nick_name", unique = true)
    private String nickName;
    @Column(unique = true)
    private String email;
    @Column
    private String pwd;

    @Column(name = "member_img")
    private String memberImg;

    @Column(length = 50)
    private String type; // 가입 방식 (예: "kakao", "naver", "direct")

    @Column(unique = true, length = 13)
    private String phone;

    @Column(name = "member_reg_date")
    private LocalDateTime regDate;

    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column(length = 255)
    private String introduce; // 자기 소개 필드 추가

    // CustomStyle과 1:1 관계 설정 (Member 삭제 시 CustomStyle도 삭제됨)
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonBackReference
    @ToString.Exclude  // 이 부분을 추가하여 toString에서 customStyle을 제외
    private CustomStyle customStyle;

    @Builder
    public Member(String nickName, String email, String pwd, String name, String phone, LocalDateTime regDate, Authority authority, String memberImg, String introduce) {
        this.nickName = nickName;
        this.email = email;
        this.pwd = pwd;
        this.phone = phone;
        this.regDate = regDate;
        this.authority = authority;
        this.memberImg = memberImg;
        this.introduce = introduce;
        // 기본값 설정
    }

    public Member(String userId, String email, String type, String phone, String name, String nickName, LocalDateTime regDate) {
        this.userId = userId;
        this.phone = phone;
        this.nickName = nickName;
        this.pwd = "password";
        this.email = email;
        this.type = type;
        this.regDate = regDate;
        this.authority = Authority.valueOf("ROLE_USER");
    }


}
