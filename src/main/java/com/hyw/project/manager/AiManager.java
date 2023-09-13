package com.hyw.project.manager;

import com.hyw.project.common.ErrorCode;
import com.hyw.project.exception.BusinessException;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 用于对接 AI 平台
 * @author 17799
 */
@Service
public class AiManager {

    @Resource
    private YuCongMingClient yuCongMingClient;

    /**
     * AI 对话
     *
     * @param modelId
     * @param message
     * @return
     */
    public String doChat(long modelId, String message) {
        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);
        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);
        if (response == null || response.getData() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }
        return Optional.ofNullable(response.getData().getContent()).orElse("");
    }
}
