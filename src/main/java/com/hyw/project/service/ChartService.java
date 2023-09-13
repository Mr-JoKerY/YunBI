package com.hyw.project.service;

import com.hyw.project.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hyw.project.model.entity.User;

import javax.servlet.http.HttpServletRequest;

/**
* @author 17799
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2023-07-01 20:52:58
*/
public interface ChartService extends IService<Chart> {

    boolean handleUpdateChartStatus(long chartId, Integer chartStatus);

    void handleChartUpdateError(long chartId, String execMessage);

    void handleChartUpdate(long chartId, String execMessage, Integer chartStatus);

    /**
     * 手动重试 AI 生成图表
     *
     * @param chartId 图表id
     * @param loginUser
     * @return boolean
     */
    boolean reloadChartByAi(long chartId, User loginUser);
}
