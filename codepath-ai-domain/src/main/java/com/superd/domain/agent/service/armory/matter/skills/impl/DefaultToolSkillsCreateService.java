package com.superd.domain.agent.service.armory.matter.skills.impl;

import com.superd.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.superd.domain.agent.model.valobj.enums.AgentTypeEnum;
import com.superd.domain.agent.service.armory.matter.skills.ToolSkillsCreateService;
import com.superd.types.enums.ResponseCode;
import com.superd.types.exception.AppException;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;


@Service
public class DefaultToolSkillsCreateService implements ToolSkillsCreateService {
    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception {

        String type = toolSkills.getType();
        String path = toolSkills.getPath();
        if (type.equals("directory")){
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsDirectory(path)
                    .build();
            return new ToolCallback[]{toolCallback};
        }
        if (type.equals("resource")){
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsResource(new ClassPathResource(path))
                    .build();
            return new ToolCallback[]{toolCallback};
        }
        throw new AppException(ResponseCode.E0002.getCode(),ResponseCode.E0002.getInfo());
    }
}
