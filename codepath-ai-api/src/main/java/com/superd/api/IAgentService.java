package com.superd.api;

import com.superd.api.dto.agent.AiAgentConfigResponseDTO;
import com.superd.api.dto.agent.ChatRequestDTO;
import com.superd.api.dto.agent.ChatResponseDTO;
import com.superd.api.dto.agent.CreateSessionRequestDTO;
import com.superd.api.dto.agent.CreateSessionResponseDTO;
import com.superd.api.response.Response;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * 智能体服务接口
 */
public interface IAgentService {

    Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList();

    Response<CreateSessionResponseDTO> createSession(CreateSessionRequestDTO requestDTO);

    Response<ChatResponseDTO> chat(ChatRequestDTO requestDTO);

    ResponseBodyEmitter chatStream(ChatRequestDTO requestDTO);

}
