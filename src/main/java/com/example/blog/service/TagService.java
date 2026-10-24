package com.example.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.blog.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    List<Tag> listAll();

    /** 新增或更新，同名标签视为重复 */
    Long saveTag(Long id, String name);

    /** 删除标签，同时清理文章-标签关联 */
    void deleteTag(Long id);
}
