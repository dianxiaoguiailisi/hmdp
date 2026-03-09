package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.LockRedis.impl.SimpleRedisLock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.sun.org.glassfish.external.statistics.annotations.Reset;
import lombok.NonNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     *  事务：createVoucherOrder没有加事务@Transactional，所以其内部调用createVoucherOrder是this.createVoucherOrder不是代理对象，所以这里事务失效
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        //1.查询优惠卷
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {

            return Result.fail("尚未开始");
        }
        //3.判断秒杀是否结束
        if (!voucher.getEndTime().isAfter(LocalDateTime.now())) {
            return Result.fail("已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1) {
            System.out.println(voucher.getStock());
            return Result.fail("库存不足 1");
        }
        //5.扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock -1")//set stock = stock -1
                .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("库存不足 2");
        }
        Long userId = UserHolder.getUser().getId();
        //单个JVM
//        synchronized (userId.toString().intern()){
//            //获取代理对象（事务）
//            IVoucherOrderService proxy =(IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        //集群获取全局JVM锁（redis）
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(5);
        if (!isLock) {
            //获取锁失败，返回错误信息
            return Result.fail("一个人只允许下一单");
        }
        try {
            IVoucherOrderService proxy =(IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在函数外对UserId加锁:先提交事务，在释放锁
     * 事务：createVoucherOrder没有加事务@Transactional，所以其内部调用
     * @param voucherId
     * @return
     */
    @Transactional//这里有两张表
    /**
     * Transactional是拿到当前对象的代理对象，做事务代理
     */
    public  Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过一次");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1:订单id
        long orderId = redisIdWorker.nextId("order");//使用redis生成的唯一ID
        voucherOrder.setId(orderId);
        //6.2：用户id
        voucherOrder.setUserId(userId);
        //6.3:代金券id
        voucherOrder.setVoucherId(voucherId);
        //写入数据库
        save(voucherOrder);
        //7.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 这里同步的锁是this(当前对象)，此时整个方法是串行执行效率低下
     * @param voucherId
     * @return
     */
    @Transactional
    public synchronized Result createVoucherOrderByThis(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过一次");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1:订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2：用户id
        voucherOrder.setUserId(userId);
        //6.3:代金券id
        voucherOrder.setVoucherId(voucherId);
        //写入数据库
        save(voucherOrder);
        //7.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 对方法内部的useriD加锁，但这里存在问题：先释放锁在提交事务
     * 若在[释放锁,提交事务]会再次出现并发问题
     * @param voucherId
     * @return
     */
    @Transactional
    public synchronized Result createVoucherOrderByUserId(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){//intern():去字符串常量池中找值一样的对象
            //查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断是否存在
            if (count > 0) {
                return Result.fail("用户已经购买过一次");
            }
            //6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //6.1:订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            //6.2：用户id
            voucherOrder.setUserId(userId);
            //6.3:代金券id
            voucherOrder.setVoucherId(voucherId);
            //写入数据库
            save(voucherOrder);
            //7.返回订单id
            return Result.ok(orderId);
        }
    }
}
