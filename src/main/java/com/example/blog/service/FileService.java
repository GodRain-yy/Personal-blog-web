package com.example.blog.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 保存上传的图片，返回可访问的 URL（如 /uploads/2026/09/xxx.png）。
     * 文件名由服务端重新生成，不使用原始文件名，避免目录穿越与覆盖。
     */
    String saveImage(MultipartFile file);
}
