package com.example.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.blog.dto.RegisterRequest;
import com.example.blog.entity.User;

public interface UserService extends IService<User> {

    /** 按用户名查，未命中返回 null */
    User getByUsername(String username);

    /** 注册，用户名或邮箱重复时抛 BusinessException */
    void register(RegisterRequest request);

    /** 登录成功后刷新最后登录时间 */
    void updateLastLoginTime(Long userId);

    /** 统计用户数，用于后台首页概览 */
    long countUsers();

    /** 查询全部已注册用户，按注册时间倒序 */
    java.util.List<User> listUsers();
}
