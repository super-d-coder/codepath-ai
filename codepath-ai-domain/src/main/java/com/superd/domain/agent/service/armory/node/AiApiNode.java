package com.superd.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.superd.domain.agent.model.entity.ArmoryCommandEntity;
import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.model.valobj.AiAgentRegisterVO;
import com.superd.domain.agent.service.armory.AbstractArmorySupport;
import com.superd.domain.agent.service.armory.factory.DefaultArmoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


@Slf4j
@Service
public class AiApiNode extends AbstractArmorySupport {

    @Resource
    ChatModelNode chatModelNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - AiApiNode");
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.AiApi aiApiConfig = aiAgentConfigTableVO.getModule().getAiApi();

        // 编写 AiApi 构建
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(aiApiConfig.getBaseUrl())
                .apiKey(aiApiConfig.getApiKey())
                .completionsPath(aiApiConfig.getCompletionsPath())
                .build();
        dynamicContext.setAiApi(openAiApi);

        // 路由到下一个节点，如果不需要路由了，可以 return 返回结果
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {

        // 如果不需要下一个节点了，可以配置 defaultStrategyHandler
        return chatModelNode;
    }

}