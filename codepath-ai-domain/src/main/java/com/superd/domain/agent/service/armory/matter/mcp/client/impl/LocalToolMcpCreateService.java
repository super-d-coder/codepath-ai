package com.superd.domain.agent.service.armory.matter.mcp.client.impl;


import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class LocalToolMcpCreateService  implements ToolMcpCreateService {

    @Resource
    private ApplicationContext applicationContext;


    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.LocalParameters localConfig = toolMcp.getLocal();
        ToolCallbackProvider mytoolcallbackprovider = applicationContext.getBean(localConfig.getName(), ToolCallbackProvider.class);
        log.info("tool local mcp initialize {}",localConfig.getName());
        return mytoolcallbackprovider.getToolCallbacks();
    }
}