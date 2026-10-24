package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.dto.RegisterRequest;
import com.example.blog.entity.User;
import com.example.blog.mapper.UserMapper;
import com.example.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public User getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username).last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        if (getByUsername(request.getUsername()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        Long emailCount = baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (emailCount != null && emailCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname()
                : request.getUsername());
        user.setRole(0);
        user.setStatus(1);
        baseMapper.insert(user);
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        if (userId == null) {
            return;
        }
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        baseMapper.updateById(user);
    }

    @Override
    public long countUsers() {
        Long count = baseMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    @Override
    public java.util.List<User> listUsers() {
        return baseMapper.selectList(
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
    }
}
