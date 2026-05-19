package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.CounterDeltas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounterService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String UPVOTES_KEY_PREFIX = "post:";
    private static final String UPVOTES_KEY_SUFFIX = ":upvotes:delta";
    private static final String COMMENTS_KEY_SUFFIX = ":comments:delta";
    private static final String VIEWS_KEY_SUFFIX = ":views:delta";

    private String upvotesKey(UUID id) {
        return UPVOTES_KEY_PREFIX + id + UPVOTES_KEY_SUFFIX;
    }

    private String commentsKey(UUID id) {
        return UPVOTES_KEY_PREFIX + id + COMMENTS_KEY_SUFFIX;
    }

    private String viewsKey(UUID id) {
        return UPVOTES_KEY_PREFIX + id + VIEWS_KEY_SUFFIX;
    }

    public void incrementUpvotes(UUID postId) {
        stringRedisTemplate.opsForValue().increment(upvotesKey(postId));
    }

    public void decrementUpvotes(UUID postId) {
        stringRedisTemplate.opsForValue().decrement(upvotesKey(postId));
    }

    public void incrementComments(UUID postId) {
        stringRedisTemplate.opsForValue().increment(commentsKey(postId));
    }

    public void decrementComments(UUID postId) {
        stringRedisTemplate.opsForValue().decrement(commentsKey(postId));
    }

    public void incrementViews(UUID postId) {
        stringRedisTemplate.opsForValue().increment(viewsKey(postId));
    }

    public CounterDeltas getDeltas(UUID postId) {
        List<String> keys = List.of(upvotesKey(postId), commentsKey(postId), viewsKey(postId));
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        return new CounterDeltas(
                parseLong(values.get(0)),
                parseLong(values.get(1)),
                parseLong(values.get(2))
        );
    }

    public Map<UUID, CounterDeltas> getDeltasBatch(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = postIds.stream().distinct().toList();

        List<String> keys = new ArrayList<>(distinct.size() * 3);
        for (UUID id : distinct) {
            keys.add(upvotesKey(id));
            keys.add(commentsKey(id));
            keys.add(viewsKey(id));
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

        Map<UUID, CounterDeltas> result = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            result.put(distinct.get(i), new CounterDeltas(
                    parseLong(values.get(i * 3)),
                    parseLong(values.get(i * 3 + 1)),
                    parseLong(values.get(i * 3 + 2))
            ));
        }
        return result;
    }

    public long getAndDeleteUpvotesDelta(UUID postId) {
        String val = stringRedisTemplate.opsForValue().getAndDelete(upvotesKey(postId));
        return parseLong(val);
    }

    public long getAndDeleteCommentsDelta(UUID postId) {
        String val = stringRedisTemplate.opsForValue().getAndDelete(commentsKey(postId));
        return parseLong(val);
    }

    public long getAndDeleteViewsDelta(UUID postId) {
        String val = stringRedisTemplate.opsForValue().getAndDelete(viewsKey(postId));
        return parseLong(val);
    }

    public Set<String> scanAllKeys(String suffix) {
        return stringRedisTemplate.keys("post:*" + suffix);
    }

    private static long parseLong(String s) {
        return s == null ? 0L : Long.parseLong(s);
    }
}
