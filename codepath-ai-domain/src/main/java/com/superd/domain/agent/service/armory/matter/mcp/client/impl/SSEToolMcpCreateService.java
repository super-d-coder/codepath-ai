package com.superd.domain.agent.service.armory.matter.mcp.client.impl;

import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.superd.types.enums.ResponseCode;
import com.superd.types.exception.AppException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;

@Service
@Slf4j
public class SSEToolMcpCreateService  implements ToolMcpCreateService {


    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();
        if (null!=sseConfig){
            String originalUri = sseConfig.getBaseUri();
            String sseEndpoint = sseConfig.getSseEndpoint();
            String baseUri = originalUri;
            if (StringUtils.isBlank(sseEndpoint)){
                URL url = new URL(originalUri);
                String protocol = url.getProtocol();
                String host = url.getHost();
                int port = url.getPort();
                baseUri = port==-1?protocol+"://"+host:protocol+"://"+host+":"+port;
                sseEndpoint="";
                int index = originalUri.indexOf(baseUri);
                if (index!=-1){
                    sseEndpoint=originalUri.substring(index+baseUri.length());
                }

            }
            sseEndpoint=StringUtils.isBlank(sseEndpoint)?"/sse":sseEndpoint;
            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder(baseUri)
                    .sseEndpoint(sseEndpoint)
                    .build();

            McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                    .requestTimeout(Duration.ofDays(sseConfig.getRequestTimeout()))
                    .build();

            var initialize = mcpSyncClient.initialize();
            log.info("tool sse mcp initialize:{}",initialize);
            return SyncMcpToolCallbackProvider.builder()
                    .mcpClients(mcpSyncClient).build()
                    .getToolCallbacks();
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),ResponseCode.ILLEGAL_PARAMETER.getInfo());
    }
}