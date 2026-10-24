package com.example.blog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.blog.entity.User;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页插件自检：造 8 条数据，按每页 5 条查询，
 * 若 PaginationInnerInterceptor 未生效会返回全部记录，断言即失败。
 */
@SpringBootTest
class PaginationTest {

    @Autowired
    private UserService userService;

    @Test
    void paginationShouldLimitRows() {
        List<Long> createdIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            User u = new User();
            u.setUsername("t_" + System.nanoTime() + "_" + i);
            u.setPassword("not-used");
            u.setEmail(u.getUsername() + "@t.local");
            u.setRole(0);
            u.setStatus(1);
            userService.save(u);
            createdIds.add(u.getId());
        }

        try {
            IPage<User> result = userService.page(new Page<>(1, 5));
            assertEquals(5, result.getRecords().size(), "每页 5 条，说明分页插件已生效");
            assertTrue(result.getTotal() >= 8, "总数应不少于 8");
        } finally {
            createdIds.forEach(userService::removeById);
        }
    }
}
