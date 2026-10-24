package com.example.blog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页返回封装（页面与 Ajax 共用）
 */
@Data
public class PageResult<T> {

    private List<T> records = Collections.emptyList();
    private Long total = 0L;
    private Long page = 1L;
    private Long size = 10L;
    private Long pages = 0L;

    public static <T> PageResult<T> of(List<T> records, Long total, Long page, Long size) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records == null ? Collections.emptyList() : records);
        r.setTotal(total == null ? 0L : total);
        r.setPage(page == null ? 1L : page);
        r.setSize(size == null || size <= 0 ? 10L : size);
        r.setPages(r.getSize() == 0 ? 0L : (r.getTotal() + r.getSize() - 1) / r.getSize());
        return r;
    }

    /**
     * 直接由 MyBatis-Plus 的 IPage 转换
     */
    public static <T> PageResult<T> of(IPage<T> p) {
        return of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 由 IPage 转换并映射元素类型（Entity -> VO）
     */
    public static <E, T> PageResult<T> of(IPage<E> p, Function<E, T> converter) {
        List<T> list = p.getRecords().stream().map(converter).toList();
        return of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }
}
