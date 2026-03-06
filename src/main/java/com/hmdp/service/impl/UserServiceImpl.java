package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    /**
     * 这里为什么使用@Resource注解：
     * 1. 在Spring Boot的架构中，依赖于IoC容器的自动装配机制，底层的连接工作已经通过配置文件完成了
     * 在Spring容器初始化的过程中，底层的 RedisAutoConfiguration 自动配置类会读取上述YAML配置，
     * 并在生命周期内自动向IoC容器中注册 RedisConnectionFactory 以及 StringRedisTemplate 等核心 Bean。
     * 2. 通过 @Resource 直接从容器中获取了已经装配好的 StringRedisTemplate 实例，并将其传递给 RefreshTokenInterceptor 拦截器使用。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，但会错误信息
            return  Result.fail("手机号格式错误");
        }
        //3.符合生成验证码
        String code = RandomUtil.randomNumbers(6);
//        //4.保存验证码到session
//        session.setAttribute("code",code);
        //4.保存验证码到redis
        stringRedisTemplate.opsForValue().set("login:code:"+phone,//key
                code,//值
                2,
                TimeUnit.MINUTES//有效期两分钟
        );
        //5.发送验证码
        log.debug("发送短信验证码成功,验证码：{}",code);
        //6.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，但会错误信息
            return  Result.fail("手机号格式错误");
        }
//        //2.校验验证码（session方式）
//        Object CacheCode = session.getAttribute("code");
//        if(CacheCode ==null || !CacheCode.toString().equals(code)){
//            //3.不一致报错
//            return  Result.fail("验证码错误");
//        }
        //3.从redis中获取验证码并校验
        String CacheCode = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        if(CacheCode ==null || !CacheCode.equals(code)){
            //3.不一致报错
            return  Result.fail("验证码错误");
        }
        //4.根据手机号，查询用户  select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user ==null) {
            //6.不存在，创建并保存
           user= createUserByPhone(phone);
        }
//        //7.保存用户信息到Session
//        session.setAttribute("user",user);
        //7.保存用户到redis中
            //7.1随机生成TOKEN，作为登录令牌
            String token = UUID.randomUUID().toString(true);
            //7.2将User对象转为Hash存储<token,user>
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString())
                    );
            //7.3存储
            String tokenKey = "login:token"+token;
            stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
            //7.4:设置数据有效期
            stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+ RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
