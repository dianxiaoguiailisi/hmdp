package com.hmdp.utils.Interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {


    /**
     * Session
     * 登录状态拦截器，用于拦截所有请求校验用户登录的状态
     * @param request 请求域
     * @param response 返回域
     * @param handler 处理器
     * @return boolean 是否放行
     * @throws Exception
     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.获取session
//        HttpSession session = request.getSession();
//        //2.获取session中的user
//        User user = (User)session.getAttribute("user");
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user, userDTO);
//        //3.判断用户是否存在
//        if (user == null) {
//            //4.不存在，,返回401
//            response.setStatus(401);
//            return false;
//        }
//        //5.存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//        //放行
//        return  true;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //1. 判断是否需要拦截(TreadLocal是否存在用户)
        if (UserHolder.getUser()== null) {
            response.setStatus(401);
            return false;
        }
        //放行
        return true;
    }

}

