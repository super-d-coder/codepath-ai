package com.superd.infrastructure.gateway.dto;

import lombok.Data;

import java.util.List;

@Data
public class GitHubSearchResponseDTO {
    private Integer total_count;
    private List<GitHubRepoDTO> items;
}
