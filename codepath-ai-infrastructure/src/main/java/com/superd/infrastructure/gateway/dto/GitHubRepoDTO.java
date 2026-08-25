package com.superd.infrastructure.gateway.dto;

import lombok.Data;
import java.util.List;

@Data
public class GitHubRepoDTO {
    private String full_name;
    private String name;
    private String description;
    private String html_url;
    private Integer stargazers_count;
    private Integer forks_count;
    private String language;
    private List<String> topics;
    private String created_at;
}
