package com.hmdp.utils.LockRedis.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.LockRedis.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 使用redis实现分布锁
 * @author zx
 * @date 2026/03/09
 */
public class SimpleRedisLock implements ILock {


    private  StringRedisTemplate stringRedisTemplate;

    private  String name;

    private static final String KEY_PREFIX = "lock";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程的标识
        String ThreadId = ID_PREFIX+Thread.currentThread().getId();//UUID+线程ID
        //获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,
                ThreadId ,
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String ThreadId = ID_PREFIX+Thread.currentThread().getId();//UUID+线程ID
        //获取锁中标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断标识是否一致
        if (ThreadId.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
