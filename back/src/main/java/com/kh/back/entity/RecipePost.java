package com.kh.back.entity;

import com.kh.back.entity.member.Member;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 사용자가 작성한 레시피의 소유권 정보를 DB에서 관리하는 엔티티.
 * - ES에는 검색용 필드(name, category, ingredients 등)만 저장
 * - DB에는 author(memberId), esDocId(ES 문서 ID), contentType 저장
 */
@Entity
@Table(name = "recipe_post")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_post_id")
    private Long id;

    /** Elasticsearch 문서 _id */
    @Column(name = "es_doc_id", nullable = false, length = 100)
    private String esDocId;

    /** 작성자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** "food" 또는 "cocktail" */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    /** 레시피 이름 (빠른 목록 조회용) */
    @Column(name = "recipe_name", length = 200)
    private String name;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
