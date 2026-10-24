package com.example.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.blog.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    List<Category> listAll();

    /** 新增或更新，同名分类视为重复 */
    Long saveCategory(Long id, String name, Integer sort);

    /** 删除分类，并把该分类下的文章置为未分类，避免出现孤儿外键 */
    void deleteCategory(Long id);

    /** 分类下的文章数 */
    long countArticles(Long categoryId);
}
