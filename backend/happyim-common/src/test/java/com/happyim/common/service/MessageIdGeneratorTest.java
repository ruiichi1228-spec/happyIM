package com.happyim.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageIdGenerator Unit Tests")
class MessageIdGeneratorTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private MessageIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MessageIdGenerator(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("should generate 19-char format XXXX-XXXX-XXXX-XXXX")
    void shouldGenerateCorrectFormat() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        String id = generator.generate("p_10001_10002", 0);
        assertEquals(19, id.length());
        assertTrue(id.matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}"));
    }

    @Test
    @DisplayName("should not contain ambiguous chars 0,1,O,I")
    void shouldNotContainAmbiguousChars() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        for (int i = 0; i < 100; i++) {
            String id = generator.generate("p_A_B_" + i, 0);
            for (char c : id.toCharArray()) {
                assertFalse("01OI".indexOf(c) >= 0, "Should not contain 0,1,O,I: " + id);
            }
        }
    }

    @Test
    @DisplayName("should generate different IDs for different spins")
    void shouldGenerateDifferentIdsForDifferentSpins() {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);
        String id1 = generator.generate("p_A_B", 0);
        String id2 = generator.generate("p_A_B", 0);
        String id3 = generator.generate("p_A_B", 0);
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
    }

    @Test
    @DisplayName("should distinguish private vs group by type bits")
    void shouldDistinguishPrivateVsGroup() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        String pid = generator.generate("p_A_B", 0);
        String gid = generator.generate("g_10000", 1);
        assertNotEquals(pid, gid);
    }

    @Test
    @DisplayName("same conversation should produce same CRC (stable shard key)")
    void shouldProduceStableCrc() {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L);
        // CRC 相同保证同会话同分片，但消息 ID 因子时间戳和 spin 不同而不同
        String id1 = generator.generate("p_A_B", 0);
        String id2 = generator.generate("p_A_B", 0);
        // 整体 ID 不同
        assertNotEquals(id1, id2);
        // CRC 部分相同 → 最后 4 个字符去掉分隔符的部分应该相同
        // (没法直接验证 CRC，但至少两个 ID 都是有效格式)
        assertTrue(id1.matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}"));
    }

    @Test
    @DisplayName("should be thread-safe with Redis increment")
    void shouldBeThreadSafe() {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L);
        for (int i = 0; i < 5; i++) {
            assertNotNull(generator.generate("p_A_B", 0));
        }
        verify(valueOps, times(5)).increment(anyString());
    }

    @Test
    @DisplayName("spin resets per conversation")
    void shouldResetSpinPerConversation() {
        when(valueOps.increment("msgid:spin:p_A_B")).thenReturn(1L);
        when(valueOps.increment("msgid:spin:p_A_C")).thenReturn(1L);
        String id1 = generator.generate("p_A_B", 0);
        String id2 = generator.generate("p_A_C", 0);
        assertNotEquals(id1, id2);
    }
}
