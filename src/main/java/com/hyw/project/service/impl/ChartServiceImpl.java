package com.hyw.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hyw.project.bizmq.BiMessageProducer;
import com.hyw.project.constant.BiConstant;
import com.hyw.project.manager.RedisLimiterManager;
import com.hyw.project.mapper.ChartMapper;
import com.hyw.project.model.entity.Chart;
import com.hyw.project.model.entity.User;
import com.hyw.project.model.enums.ChartStatusEnum;
import com.hyw.project.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author 17799
 * @description 针对表【chart(图表信息表)】的数据库操作Service实现
 * @createDate 2023-07-01 20:52:58
 */
@Slf4j
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements ChartService {

    @Resource
    private BiMessageProducer biMessageProducer;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Override
    public boolean handleUpdateChartStatus(long chartId, Integer chartStatus) {
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus(chartStatus);
        return this.updateById(updateChart);
    }

    @Override
    public void handleChartUpdateError(long chartId, String execMessage) {
        handleChartUpdate(chartId, execMessage, ChartStatusEnum.FAILED.getValue());
    }

    @Override
    public void handleChartUpdate(long chartId, String execMessage, Integer chartStatus) {
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setStatus(chartStatus);
        if (execMessage != null) {
            updateChart.setExecMessage(execMessage);
        }
        boolean updateResult = this.updateById(updateChart);
        if (!updateResult) {
            log.error("更新图表状态失败" + chartId + "," + execMessage);
        }
    }

    @Override
    public boolean reloadChartByAi(long chartId, User loginUser) {
        final String key = BiConstant.BI_REDIS_LIMITER_KEY + loginUser.getId();
        // 限流判断
        redisLimiterManager.doRateLimit(key);
        // 发送消息
        biMessageProducer.sendMessage(String.valueOf(chartId));
        return true;
    }
}




