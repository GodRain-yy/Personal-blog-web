package com.example.blog.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vditor 约定的上传返回结构：
 * {"code":0,"msg":"","data":{"errFiles":[],"succMap":{"a.png":"/uploads/xxx.png"}}}
 *
 * 注意：内部类不能命名为 Data，否则会与 Lombok 的 @Data 注解同名冲突，
 * 导致注解处理器解析失败（曾使整个项目的 getter/setter 全部生成失败）。
 */
@Data
public class VditorUploadResponse {

    private int code;

    private String msg;

    private UploadData data = new UploadData();

    public static VditorUploadResponse success(String originalName, String url) {
        VditorUploadResponse r = new VditorUploadResponse();
        r.setCode(0);
        r.setMsg("");
        r.getData().getSuccMap().put(originalName == null ? "file" : originalName, url);
        return r;
    }

    public static VditorUploadResponse fail(String originalName, String message) {
        VditorUploadResponse r = new VditorUploadResponse();
        r.setCode(1);
        r.setMsg(message);
        r.getData().getErrFiles().add(originalName == null ? "file" : originalName);
        return r;
    }

    @Data
    public static class UploadData {

        private Map<String, String> succMap = new HashMap<>();

        private List<String> errFiles = new ArrayList<>();
    }
}
