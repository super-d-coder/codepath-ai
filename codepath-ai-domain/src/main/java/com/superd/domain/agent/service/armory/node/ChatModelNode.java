package com.superd.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.superd.domain.agent.model.entity.ArmoryCommandEntity;
import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.model.valobj.AiAgentRegisterVO;
import com.superd.domain.agent.service.armory.AbstractArmorySupport;
import com.superd.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.superd.domain.agent.service.armory.matter.mcp.client.ToolMcpCreateService;
import com.superd.domain.agent.service.armory.matter.mcp.client.factory.DefaultMcpClientFactory;
import com.superd.domain.agent.service.armory.matter.skills.impl.DefaultToolSkillsCreateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {

    @Resource
    private AgentNode agentNode;

    @Resource
    private DefaultMcpClientFactory mcpClientFactory;

    @Resource
    private DefaultToolSkillsCreateService toolSkillsCreateService;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - ChatModelNode");
        //1.从上下文获得aiapi
        OpenAiApi openAiApi = dynamicContext.getAiApi();

        //2.获取yml
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();

        //3.装载mcp
        log.info("MCP装配开始");
        List<ToolCallback>  toolCallbacks = new ArrayList<>();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();

        if (toolMcpList!=null && !toolMcpList.isEmpty()){
            for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList){
                ToolMcpCreateService tooMcpCreateService = mcpClientFactory.getTooMcpCreateService(toolMcp);
                ToolCallback[] toolCallback1 = tooMcpCreateService.buildToolCallback(toolMcp);
                toolCallbacks.addAll(List.of(toolCallback1));
            }
        }


        //4.装载skills
        log.info("Skills装配开始");
        List<AiAgentConfigTableVO.Module.ChatModel.ToolSkills> toolSkillsList = chatModelConfig.getToolSkillsList();
        if (toolSkillsList!=null && !toolSkillsList.isEmpty()){
            toolSkillsList.forEach(toolSkills -> {
                try {
                    ToolCallback[] toolCallbacks2 = toolSkillsCreateService.buildToolCallback(toolSkills);
                    toolCallbacks.addAll(List.of(toolCallbacks2));
                } catch (Exception e) {
                    log.error("Skill装载出错");
                    throw new RuntimeException(e.getMessage(),e.getCause());
                }
            });
        }

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelConfig.getModel())
                        .toolCallbacks(toolCallbacks)
                        .build())
                .build();

        dynamicContext.setChatModel(chatModel);

        return router(requestParameter,dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentNode;
    }
}
