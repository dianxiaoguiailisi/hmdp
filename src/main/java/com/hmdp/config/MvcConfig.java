package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * SpringMVC拦截器注册
 * @author zx
 * @date 2026/03/06
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    /**
     * 添加拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())//注册拦截器
                .excludePathPatterns(//拦截路劲（排除）
                        "/user/code","/user/login","/blog/hot","/shop/**","shop-type/**","/voucher/**"
                );
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate));//拦截所有请求.....
    }
}
