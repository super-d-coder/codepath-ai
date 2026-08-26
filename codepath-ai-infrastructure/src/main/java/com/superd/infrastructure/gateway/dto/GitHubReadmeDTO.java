package com.superd.infrastructure.gateway.dto;

import lombok.Data;

/** GitHub README API 响应。content 为 Base64 编码。 */
@Data
public class GitHubReadmeDTO {
    private String name;
    private String path;
    private String html_url;
    private String download_url;
    private String content;
    private String encoding;
}
