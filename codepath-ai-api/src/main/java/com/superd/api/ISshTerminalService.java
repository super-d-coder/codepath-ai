package com.superd.api;

import com.superd.api.dto.ssh.*;
import com.superd.api.response.Response;

public interface ISshTerminalService {

    /** 打开终端会话 */
    Response<TerminalOpenResponseDTO> openTerminal(TerminalOpenRequestDTO requestDTO);

    /** 执行命令（整行模式） */
    Response<TerminalExecResponseDTO> execCommand(TerminalExecRequestDTO requestDTO);

    /** 写入原始输入（按键、粘贴等） */
    Response<Void> writeToTerminal(TerminalWriteRequestDTO requestDTO);

    /** 读取终端输出 */
    Response<TerminalReadResponseDTO> readFromTerminal(String sessionId);

    /** 调整终端窗口大小 */
    Response<Void> resizeTerminal(TerminalResizeRequestDTO requestDTO);

    /** 关闭终端会话 */
    Response<Void> closeTerminal(String sessionId);
}