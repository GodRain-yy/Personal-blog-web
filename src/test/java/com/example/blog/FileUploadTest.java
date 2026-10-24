package com.example.blog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.blog.entity.User;
import com.example.blog.mapper.UserMapper;
import com.example.blog.security.LoginUser;
import com.example.blog.service.FileService;
import com.example.blog.vo.VditorUploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片上传自检：文件校验 + 返回结构符合 Vditor 约定。
 *
 * Spring Boot 4 已移除 @AutoConfigureMockMvc，这里直接调用 Controller 方法做验证，
 * 不依赖 Servlet 测试上下文。未登录拦截由 SecurityConfig 保证，用 curl 在集成阶段验证。
 */
@SpringBootTest
@TestPropertySource(properties = "blog.upload.dir=target/test-uploads")
class FileUploadTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4
    };

    @Autowired
    private FileService fileService;

    @Autowired
    private com.example.blog.controller.UploadController uploadController;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldSaveImageAndReturnUrl() {
        String url = fileService.saveImage(new MockMultipartFile("file", "photo.png", "image/png", PNG_BYTES));

        assertTrue(url.startsWith("/uploads/"), "应返回可访问 URL: " + url);
        assertTrue(url.endsWith(".png"), "扩展名应保留");
        Path saved = Paths.get("target/test-uploads").resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(saved), "文件应真实落盘: " + saved);
    }

    @Test
    void shouldRejectNonImageContentType() {
        var file = new MockMultipartFile("file", "evil.html", "text/html", "<script>x</script>".getBytes());
        assertThrows(RuntimeException.class, () -> fileService.saveImage(file), "非图片类型必须被拒绝");
    }

    @Test
    void shouldRejectDisallowedExtension() {
        var file = new MockMultipartFile("file", "a.exe", "image/png", PNG_BYTES);
        assertThrows(RuntimeException.class, () -> fileService.saveImage(file), "非白名单扩展名必须被拒绝");
    }

    @Test
    void shouldRejectTooLargeFile() {
        var file = new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024]);
        assertThrows(RuntimeException.class, () -> fileService.saveImage(file), "超过 5MB 必须被拒绝");
    }

    @Test
    void shouldRejectEmptyFile() {
        var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThrows(RuntimeException.class, () -> fileService.saveImage(file), "空文件必须被拒绝");
    }

    @Test
    void shouldNotUseOriginalFileName() {
        // 原始文件名带路径穿越片段，服务端必须重新生成文件名
        String url = fileService.saveImage(
                new MockMultipartFile("file", "../../../evil.png", "image/png", PNG_BYTES));
        assertTrue(!url.contains("..") && !url.contains("evil"), "文件名必须由服务端生成: " + url);
    }

    @Test
    void shouldReturnVditorFormatWhenLoggedIn() {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        assertNotNull(admin, "管理员账号应存在");

        VditorUploadResponse res = uploadController.uploadImage(
                new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES), new LoginUser(admin));

        assertEquals(0, res.getCode(), "Vditor 要求 code=0 表示成功");
        assertTrue(res.getData().getSuccMap().containsKey("a.png"), "succMap 应包含上传后的 URL");
        assertTrue(res.getData().getErrFiles().isEmpty());
    }

    @Test
    void shouldFailWithoutLogin() {
        VditorUploadResponse res = uploadController.uploadImage(
                new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES), null);

        assertEquals(1, res.getCode(), "未登录应返回失败");
        assertTrue(res.getData().getSuccMap().isEmpty());
        assertEquals(1, res.getData().getErrFiles().size());
    }
}
