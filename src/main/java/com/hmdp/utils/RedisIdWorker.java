package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 使用Redis生成全局唯一ID
 * @author zx
 * @date 2026/03/09
 */
@Component
public class RedisIdWorker {
    /*开始的时间戳*/
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /*序列号的位数*/
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *
     * @param keyPrefix key前缀
     * @return long
     */
    public long nextId(String keyPrefix){
        //1.生成时间戳:
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
            //2.1:获取当前日期作为key，避免超过2^32位上限
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
            //2.2：自增长：以日期作为key，来进行自增
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回

        return timestamp<<COUNT_BITS|count;
    }


}
