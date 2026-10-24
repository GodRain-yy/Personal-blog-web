package com.example.blog.vo;

import lombok.Data;

import java.util.List;

@Data
public class ArchiveGroupVO {

    /** 形如 2026-09 */
    private String yearMonth;

    private List<ArchiveItemVO> articles;
}
