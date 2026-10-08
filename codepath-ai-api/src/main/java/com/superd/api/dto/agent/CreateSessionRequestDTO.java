package com.superd.api.dto.agent;

import lombok.Data;

@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}
