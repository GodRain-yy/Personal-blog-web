package com.example.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArchiveItemVO {

    private Long id;

    private String title;

    private LocalDateTime publishTime;
}
