package com.hyw.project.bizmq;

import com.hyw.project.common.ErrorCode;
import com.hyw.project.constant.BiConstant;
import com.hyw.project.constant.BiMqConstant;
import com.hyw.project.exception.BusinessException;
import com.hyw.project.manager.AiManager;
import com.hyw.project.model.entity.Chart;
import com.hyw.project.model.enums.ChartStatusEnum;
import com.hyw.project.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * bi 消息重试 消费者
 *
 * @author hyw
 */
@Slf4j
@Component
public class BIMessageReloadConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    // 指定程序监听的消息队列和确认机制
//    @RabbitListener(queues = {BiMqConstant.BI_RELOAD_QUEUE_NAME}, ackMode = "MANUAL")
    public void biReceiveMessage(String message, Channel channel, @Header(value = AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("biReceiveMessage message = {} deliveryTag = {}", message, deliveryTag);
        try {
            if (StringUtils.isBlank(message)) {
                channel.basicNack(deliveryTag, false, false);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
            }
            long chartId = Long.parseLong(message);
            Chart chart = chartService.getById(chartId);
            if (ObjectUtils.isEmpty(chart)) {
                channel.basicNack(deliveryTag, false, false);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
            }

            if (ChartStatusEnum.SUCCEED.getValue().intValue() == chart.getStatus().intValue()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请不要重复生成");
            }

            // 修改图表状态为 reload
            boolean updateRes = chartService.handleUpdateChartStatus(chartId, ChartStatusEnum.RETRY.getValue());
            if (!updateRes) {
                channel.basicNack(deliveryTag, false, false);
                chartService.handleChartUpdateError(chartId, "更新图表状态执行中失败");
                return;
            }

            // 向AI提问
            String userInput = buildUserInput(chart);
            String aiRes = aiManager.doChat(BiConstant.BI_MODEL_ID, userInput);
            // 处理AI返回数据，得到 图表数据 和 分析建议
            String[] aiData = aiRes.split(BiConstant.AI_SPLIT_STR);
            log.info("aiData len = {} data = {}", aiData.length, aiRes);
            if (aiData.length < 3) {
                channel.basicNack(deliveryTag, false, false);
                chartService.handleChartUpdateError(chartId, "Ai生成有误");
                return;
            }
            String genChart = aiData[1].trim();
            String genResult = aiData[2].trim();

            // 更新 图表数据
            Chart updateChart = new Chart();
            updateChart.setId(chartId);
            updateChart.setGenChart(genChart);
            updateChart.setGenResult(genResult);
            updateChart.setStatus(ChartStatusEnum.SUCCEED.getValue());
            if (!chartService.updateById(updateChart)) {
                channel.basicNack(deliveryTag, false, false);
                chartService.handleChartUpdateError(chartId, "更新图表失败");
            }

            // 确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("拒绝消息失败 error = {}", ex.getMessage());
            }
            log.error("任务处理失败 message = {} deliveryTag = {} error = {}", message, deliveryTag, e.getMessage());
        }
    }

    /**
     * 构建用户输入
     * @param chart
     * @return
     */
    private String buildUserInput(Chart chart) {
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }
}