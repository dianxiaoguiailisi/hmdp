package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
     private  StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 使用redis缓存实现商户查询。
     * 先查询redis中是否存在店铺信息，若存在则返回；不存在，则根据id查询数据库并写入redis，最后返回数据
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //1. 缓存穿透
       // Shop shop = queryWithPassThrough(id);

        //2.使用互斥锁解决缓存击穿
       Shop shop = queryWithMutex(id);
        //3.使用逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
//        if (shop == null) {
//            return Result.fail("店铺不存在");
//        }
        return Result.ok(shop);
    }

    /**
     * 使用逻辑过期解决缓存击穿
     * @param id
     * @return <p>
     */
    public Shop queryWithLogicalExpire(Long id){
        String key = "cache:shop:" + id;
        //1.从redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.存在直接返回
            return null;
        }
        //4. 命中，需要将json反序列化对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1：未过期直接返回
            return shop;
        }
        //5.2:已过期，需要缓存重建
        //6.缓存重建
        //6.1：获取互斥锁
        String lockKey = "lock:shop:"+id;
        boolean islock = tryLock(lockKey);
        //6.2：是否获取锁成功
        if (islock) {
            //6.3：成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);//释放锁
                }


            });
        }
        //6.4：失败返回过期的商品信息
        return shop;
    }

    /**
     * 缓存击穿解决方案
     * @param id
     * @return <p>
     */
    public Shop queryWithPassThrough(Long id){
        String key = "cache:shop:" + id;
        //1.从redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return null;
        }
        //判断是否是空值
        if(shopJson != null){
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //将空值写入redis防止缓存击穿
            stringRedisTemplate.opsForValue().set(key, "",2L, TimeUnit.MINUTES);
            return null;
        }
        //5.存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        return shop;
    }


    /**
     * 使用互斥解决缓存击穿
     * @param id
     * @return <p>
     */
    public Shop queryWithMutex(Long id)  {
        String key = "cache:shop:" + id;
        //1.从redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if(shopJson != null){
            return null;
        }
        //4.实现缓存重建
        //4.1：获取互斥锁
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            //4.2：判断是否获取成功
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //4.3：失败，进入休眠并重试
                Thread.sleep(50);
                return  queryWithMutex(id);
            }
            //4.4成功，根据id查询数据库
            shop = getById(id);
            //5.不存在，返回错误
            if (shop == null) {
                //将空值写入redis防止缓存击穿
                stringRedisTemplate.opsForValue().set(key, "",2L, TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
            //7.释放互斥锁
            unlock(lockKey);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }

    /**
     * 更新店铺信息（需要事务的一致性）
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return  Result.fail("店铺ID不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除redis缓存
        stringRedisTemplate.delete("cache:shop:"+id);
        return Result.ok();
    }
    private void saveShop2Redis(Long id, Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set("cache:shop:"+id,JSONUtil.toJsonStr(redisData));
    }
    /**
     * 获取锁
     * @param key
     * @return boolean
     */
    private  boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     * @return boolean
     */
    private  void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
