package com.kh.back.service.redis;

import com.kh.back.constant.ReactionType;
import com.kh.back.dto.recipe.res.ReactionSummaryResDto;
import com.kh.back.service.action.ReActionService;
import com.kh.back.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private static final String POST_PREFIX = "post:";
    private static final String REACTIONS_SUFFIX = ":reactions";
    private static final String LIKE_COUNT_SUFFIX = ":likeCount";
    private static final String DISLIKE_COUNT_SUFFIX = ":dislikeCount";

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberService memberService;
    private final ReActionService reActionService;

    public ReactionType getCurrentUserReaction(Authentication authentication, String postId, String type) {
        if (authentication == null) {
            return ReactionType.NONE;
        }
        String userId = String.valueOf(memberService.getMemberId(authentication));
        return getCurrentReactionInternal(authentication, postId, type, userId);
    }
    public ReactionSummaryResDto getReactionSummary(
            Authentication authentication,
            String postId,
            String type,
            long baseLikeCount,
            long baseDislikeCount
    ) {
        long likeCount = getOrInitializeCount(getLikeCountKey(postId, type), baseLikeCount);
        long dislikeCount = getOrInitializeCount(getDislikeCountKey(postId, type), baseDislikeCount);
        ReactionType currentReaction = getCurrentUserReaction(authentication, postId, type);

        return buildSummary(postId, type, likeCount, dislikeCount, currentReaction);
    }
    public ReactionSummaryResDto updateReaction(
            Authentication authentication,
            String postId,
            String type,
            ReactionType requestedReaction,
            long baseLikeCount,
            long baseDislikeCount
    ) {
        if (authentication == null) {
            throw new InsufficientAuthenticationException("반응 기능은 로그인 후 사용할 수 있습니다.");
        }
        if (requestedReaction == null || requestedReaction == ReactionType.NONE) {
            throw new IllegalArgumentException("요청 반응값은 LIKE 또는 DISLIKE 여야 합니다.");
        }
        String userId = String.valueOf(memberService.getMemberId(authentication));
        String likeCountKey = getLikeCountKey(postId, type);
        String dislikeCountKey = getDislikeCountKey(postId, type);
        long likeCount = getOrInitializeCount(likeCountKey, baseLikeCount);
        long dislikeCount = getOrInitializeCount(dislikeCountKey, baseDislikeCount);
        ReactionType currentReaction = getCurrentReactionInternal(authentication, postId, type, userId);
        ReactionType nextReaction = resolveNextReaction(currentReaction, requestedReaction);
        reActionService.syncReaction(authentication, postId, type, nextReaction);
        long updatedLikeCount = likeCount;
        long updatedDislikeCount = dislikeCount;
        if (currentReaction == ReactionType.LIKE && nextReaction != ReactionType.LIKE) {
            updatedLikeCount = decrementNonNegative(likeCountKey, updatedLikeCount);
        }
        if (currentReaction == ReactionType.DISLIKE && nextReaction != ReactionType.DISLIKE) {
            updatedDislikeCount = decrementNonNegative(dislikeCountKey, updatedDislikeCount);
        }
        if (nextReaction == ReactionType.LIKE && currentReaction != ReactionType.LIKE) {
            updatedLikeCount = increment(likeCountKey, updatedLikeCount);
        }
        if (nextReaction == ReactionType.DISLIKE && currentReaction != ReactionType.DISLIKE) {
            updatedDislikeCount = increment(dislikeCountKey, updatedDislikeCount);
        }
        updateUserReactionState(postId, type, userId, nextReaction);

        log.info("[updateReaction] memberId={}, postId={}, type={}, currentReaction={}, requestedReaction={}, nextReaction={}, likeCount={}, dislikeCount={}",
                userId, postId, type, currentReaction, requestedReaction, nextReaction, updatedLikeCount, updatedDislikeCount);

        return buildSummary(postId, type, updatedLikeCount, updatedDislikeCount, nextReaction);
    }
    public List<Map<String, Object>> getAllReactionCounts() {
        Set<String> baseKeys = collectReactionBaseKeys();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String baseKey : baseKeys) {
            String[] parts = parsePostKey(baseKey);
            if (parts == null) {
                continue;
            }
            long likeCount = readLong(getLikeCountKey(parts[0], parts[1])).orElse(0L);
            long dislikeCount = readLong(getDislikeCountKey(parts[0], parts[1])).orElse(0L);
            result.add(Map.of(
                    "postId", parts[0],
                    "type", parts[1],
                    "likeCount", likeCount,
                    "dislikeCount", dislikeCount
            ));
        }

        return result;
    }

    private ReactionType getCurrentReactionInternal(Authentication authentication, String postId, String type, String userId) {
        Object storedReaction = hashOps().get(getReactionHashKey(postId, type), userId);
        if (storedReaction != null) {
            return ReactionType.valueOf(String.valueOf(storedReaction));
        }

        ReactionType reactionFromDb = reActionService.getCurrentReaction(authentication, postId, type);
        if (reactionFromDb != ReactionType.NONE) {
            hashOps().put(getReactionHashKey(postId, type), userId, reactionFromDb.name());
        }
        return reactionFromDb;
    }

    private void updateUserReactionState(String postId, String type, String userId, ReactionType nextReaction) {
        String reactionHashKey = getReactionHashKey(postId, type);
        if (nextReaction == ReactionType.NONE) {
            hashOps().delete(reactionHashKey, userId);
            return;
        }
        hashOps().put(reactionHashKey, userId, nextReaction.name());
    }

    private ReactionType resolveNextReaction(ReactionType currentReaction, ReactionType requestedReaction) {
        return currentReaction == requestedReaction ? ReactionType.NONE : requestedReaction;
    }

    private long getOrInitializeCount(String key, long initialValue) {
        return readLong(key).orElseGet(() -> {
            redisTemplate.opsForValue().set(key, String.valueOf(initialValue));
            return initialValue;
        });
    }

    private long increment(String key, long fallback) {
        Long updated = redisTemplate.opsForValue().increment(key, 1);
        return updated != null ? updated : fallback + 1;
    }

    private long decrementNonNegative(String key, long currentValue) {
        long nextValue = Math.max(currentValue - 1, 0);
        redisTemplate.opsForValue().set(key, String.valueOf(nextValue));
        return nextValue;
    }

    private Set<String> collectReactionBaseKeys() {
        Set<String> result = new LinkedHashSet<>();
        addMatchingKeys(result, "*" + REACTIONS_SUFFIX);
        addMatchingKeys(result, "*" + LIKE_COUNT_SUFFIX);
        addMatchingKeys(result, "*" + DISLIKE_COUNT_SUFFIX);
        return result;
    }

    private void addMatchingKeys(Set<String> collector, String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null) {
            return;
        }
        keys.stream()
                .map(this::stripKeySuffix)
                .filter(Objects::nonNull)
                .forEach(collector::add);
    }

    private String stripKeySuffix(String key) {
        if (key.endsWith(REACTIONS_SUFFIX)) {
            return key.substring(0, key.length() - REACTIONS_SUFFIX.length());
        }
        if (key.endsWith(LIKE_COUNT_SUFFIX)) {
            return key.substring(0, key.length() - LIKE_COUNT_SUFFIX.length());
        }
        if (key.endsWith(DISLIKE_COUNT_SUFFIX)) {
            return key.substring(0, key.length() - DISLIKE_COUNT_SUFFIX.length());
        }
        return null;
    }

    private String[] parsePostKey(String baseKey) {
        String[] parts = baseKey.split(":");
        if (parts.length != 3 || !"post".equals(parts[0])) {
            log.warn("[parsePostKey] Invalid Redis base key={}", baseKey);
            return null;
        }
        return new String[]{parts[1], parts[2]};
    }

    private Optional<Long> readLong(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.of(Long.parseLong(String.valueOf(value)));
    }

    private HashOperations<String, Object, Object> hashOps() {
        return redisTemplate.opsForHash();
    }

    private ReactionSummaryResDto buildSummary(String postId, String type, long likeCount, long dislikeCount, ReactionType currentReaction) {
        return ReactionSummaryResDto.builder()
                .postId(postId)
                .type(type)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .currentUserReaction(currentReaction)
                .build();
    }

    private String getReactionHashKey(String postId, String type) {
        return POST_PREFIX + postId + ":" + type + REACTIONS_SUFFIX;
    }

    private String getLikeCountKey(String postId, String type) {
        return POST_PREFIX + postId + ":" + type + LIKE_COUNT_SUFFIX;
    }

    private String getDislikeCountKey(String postId, String type) {
        return POST_PREFIX + postId + ":" + type + DISLIKE_COUNT_SUFFIX;
    }
}
