package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${blog.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 启动时确保上传目录存在。
     * 否则 WebMvcConfig 里 /uploads/** 映射到一个不存在的目录，访问图片会 500 而不是 404。
     */
    @jakarta.annotation.PostConstruct
    public void initUploadDir() {
        try {
            Files.createDirectories(Paths.get(uploadDir).toAbsolutePath().normalize());
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", uploadDir, e);
        }
    }

    @Override
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能上传图片文件");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 " + String.join("/", ALLOWED_EXTENSIONS) + " 格式");
        }

        // 文件名由服务端生成，杜绝 ../ 穿越与同名覆盖
        String datePath = LocalDate.now().format(DATE_PATH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = Paths.get(uploadDir).resolve(datePath).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return "/uploads/" + datePath + "/" + filename;
        } catch (IOException e) {
            log.error("图片保存失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
    }

    private String extractExtension(String originalFilename) {
        String clean = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int dot = clean.lastIndexOf('.');
        if (dot < 0 || dot == clean.length() - 1) {
            return "";
        }
        return clean.substring(dot + 1).toLowerCase();
    }
}
