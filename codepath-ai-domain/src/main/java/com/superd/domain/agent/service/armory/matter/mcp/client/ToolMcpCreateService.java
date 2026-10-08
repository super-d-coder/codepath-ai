package com.superd.domain.agent.service.armory.matter.mcp.client;

import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**
 * 工具 MCP 构建服务
 * 2026/1/2 09:31
 */
public interface ToolMcpCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;

}
