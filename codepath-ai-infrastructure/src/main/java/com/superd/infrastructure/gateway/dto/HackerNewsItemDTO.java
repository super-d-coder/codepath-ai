package com.superd.infrastructure.gateway.dto;

import lombok.Data;

@Data
public class HackerNewsItemDTO {
    private Long id;
    private String type;
    private String title;
    private String url;
    private String by;
    private Long time;
    private Integer score;
    private Integer descendants;
}
