package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.security.LoginUser;
import com.example.blog.service.FileService;
import com.example.blog.vo.VditorUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传。返回纯 JSON，不返回 HTML 片段（架构硬约束 1）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;

    @PostMapping("/upload/image")
    public VditorUploadResponse uploadImage(@RequestParam("file") MultipartFile file,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        String originalName = file == null ? null : file.getOriginalFilename();
        if (loginUser == null) {
            return VditorUploadResponse.fail(originalName, "请先登录");
        }
        try {
            String url = fileService.saveImage(file);
            return VditorUploadResponse.success(originalName, url);
        } catch (BusinessException e) {
            return VditorUploadResponse.fail(originalName, e.getMessage());
        } catch (Exception e) {
            log.error("上传异常", e);
            return VditorUploadResponse.fail(originalName, "上传失败");
        }
    }
}
