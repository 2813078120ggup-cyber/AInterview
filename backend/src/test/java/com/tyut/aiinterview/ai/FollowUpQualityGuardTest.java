package com.tyut.aiinterview.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FollowUpQualityGuardTest {
    private final FollowUpQualityGuard guard = new FollowUpQualityGuard();

    @Test
    void acceptsOneDistinctQuestion() {
        assertTrue(guard.rejectionReason("你提到使用内存屏障，具体是哪一类屏障保证了写入可见性？",
                List.of("volatile 为什么能够保证线程之间的可见性？")).isBlank());
    }

    @Test
    void rejectsTransitionsMultipleQuestionsAndDuplicates() {
        assertFalse(guard.rejectionReason("这部分回答不错。下一题请说明 synchronized 的原理？", List.of()).isBlank());
        assertFalse(guard.rejectionReason("这个机制如何工作？又有哪些边界？", List.of()).isBlank());
        assertFalse(guard.rejectionReason("volatile为什么能够保证线程之间的可见性？",
                List.of("volatile 为什么能够保证线程之间的可见性？")).isBlank());
    }

    @Test
    void fallbackIsAlwaysAValidUnaskedQuestion() {
        String fallback = guard.fallback(List.of("你刚才提到的关键机制，在什么边界条件下可能失效或需要额外处理？"));
        assertTrue(guard.rejectionReason(fallback,
                List.of("你刚才提到的关键机制，在什么边界条件下可能失效或需要额外处理？")).isBlank());
    }
}
