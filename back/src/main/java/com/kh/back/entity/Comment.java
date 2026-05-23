package com.kh.back.entity;




import com.kh.back.entity.member.Member;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Comment {


    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.AUTO) // JPA가 자동으로 생성 전략을 정함
    private Long commentId;

    private String recipeId; // 레시피와 연관관계

    @ManyToOne(fetch = FetchType.LAZY) // 멤버 테이블과 연관 관계 설정
    private Member member; // 댓글 작성자

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각 (수동으로 처리)

    @Column(nullable = false)
    private boolean deleted;

    @ManyToOne(fetch = FetchType.LAZY) // 부모 댓글과 연관 관계 설정 (대댓글인 경우)
    @JoinColumn(name = "parent_comment_id") // 부모 댓글과 연결될 필드
    private Comment parentComment; // 부모 댓글

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>(); // 대댓글 목록

    // 댓글 생성 시 자동으로 현재 시각을 설정
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(); // 자동으로 현재 시각을 저장
        }
        deleted = false;
    }
}
