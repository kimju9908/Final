package com.kh.back.repository;

import com.kh.back.entity.RecipePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecipePostRepository extends JpaRepository<RecipePost, Long> {

    /** 특정 회원의 레시피 목록을 최신순으로 조회 */
    Page<RecipePost> findByMember_MemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    /** ES 문서 ID로 레시피 조회 */
    Optional<RecipePost> findByEsDocId(String esDocId);

    /** ES 문서 ID + contentType 으로 조회 */
    Optional<RecipePost> findByEsDocIdAndContentType(String esDocId, String contentType);
}
