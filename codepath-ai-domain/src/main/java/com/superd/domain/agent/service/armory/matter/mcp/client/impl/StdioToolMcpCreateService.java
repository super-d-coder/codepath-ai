package com.superd.domain.agent.service.armory.matter.mcp.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.superd.types.enums.ResponseCode;
import com.superd.types.exception.AppException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class StdioToolMcpCreateService  implements ToolMcpCreateService {


    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters stdioConfig = toolMcp.getStdio();
        if (null!=stdioConfig){
            AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters.ServerParameters serverParameters = stdioConfig.getServerParameters();
            var stdioParams = ServerParameters.builder(serverParameters.getCommand())
                    .args(serverParameters.getArgs())
                    .env(serverParameters.getEnv())
                    .build();

            var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                    .requestTimeout(Duration.ofSeconds(stdioConfig.getRequestTimeout())).build();
            var init_stdio = mcpClient.initialize();

            log.info("Tool Stdio MCP Initialized {}", init_stdio);
            return SyncMcpToolCallbackProvider.builder()
                    .mcpClients(mcpClient).build()
                    .getToolCallbacks();
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),ResponseCode.ILLEGAL_PARAMETER.getInfo());
    }
}