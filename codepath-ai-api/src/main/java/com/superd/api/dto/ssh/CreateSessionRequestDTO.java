package com.superd.api.dto.ssh;

import lombok.Data;

@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}
