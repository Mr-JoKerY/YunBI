package com.hyw.project.bizmq;

import com.github.rholder.retry.*;
import com.google.common.base.Predicates;
import com.hyw.project.common.ErrorCode;
import com.hyw.project.constant.BiConstant;
import com.hyw.project.constant.BiMqConstant;
import com.hyw.project.constant.CommonConstant;
import com.hyw.project.exception.BusinessException;
import com.hyw.project.manager.AiManager;
import com.hyw.project.model.entity.Chart;
import com.hyw.project.model.enums.ChartStatusEnum;
import com.hyw.project.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.RetryException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.hyw.project.constant.BiConstant.AI_SPLIT_STR;
import static com.hyw.project.constant.BiConstant.BI_MODEL_ID;

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    private int redeliveryCount = 1;

    // 指定程序监听的消息队列和确认机制
    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        // 限制每个消费者同时只能处理一个任务
        channel.basicQos(1);
        log.info("receiveMessage message = {}", message);
        if (StringUtils.isBlank(message)) {
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
        }
        // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
        boolean b = chartService.handleUpdateChartStatus(chartId, ChartStatusEnum.RUNNING.getValue());
        if (!b) {
            channel.basicNack(deliveryTag, false, false);
            chartService.handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
            return;
        }

//        String result = null;
//        try {
//            result = aiManager.doChat(CommonConstant.MODEL_ID, buildUserInput(chart));
//        } catch (Exception e) {
//            handleChartUpdate(chart.getId(), e.getMessage(), StatusEnum.retry);
//            if (redeliveryCount < 4) {
//                chanenel.basicNack(deliveryTag, false, true);
//                log.info("重试次数：{}", redeliveryCount);
//                redeliveryCount++;
//                Thread.sleep(30000);
//                return;
//            }
//            redeliveryCount = 1;
//            handleChartUpdate(chart.getId(), e.getMessage(), StatusEnum.failed);
//            chanenel.basicReject(deliveryTag, false);
//        }

        // 调用 AI
        Callable<String> callable = () -> aiManager.doChat(BI_MODEL_ID, buildUserInput(chart));
        Long param = chart.getId(); // 这里是示例参数，你需要根据自己的逻辑来设置
        // 定义重试器
        Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
                .retryIfResult(Predicates.<String>isNull()) // 如果结果为空则重试
                .retryIfExceptionOfType(IOException.class) // 发生IO异常则重试
                .retryIfRuntimeException() // 发生运行时异常则重试
                .withWaitStrategy(WaitStrategies.incrementingWait(6, TimeUnit.SECONDS, 18, TimeUnit.SECONDS)) // 等待
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 允许执行3次（首次执行 + 最多重试2次）
                .withRetryListener(new RetryListener() {
                    private final Long chartId = param; // 在匿名内部类中引用外部参数

                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        chartService.handleChartUpdate(chartId, "AI 生成错误", ChartStatusEnum.RETRY.getValue());
                    }
                })
                .build();
        try {
            String result = retryer.call(callable); // 执行
            if (StringUtils.isBlank(result)) {
                return;
            }
            String[] aiData = result.split(AI_SPLIT_STR);
            log.info("aiData len = {} data = {}", aiData.length, aiData);
            if (aiData.length < 3) {
                channel.basicNack(deliveryTag, false, false);
                chartService.handleChartUpdateError(chart.getId(), "AI 生成错误");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
            }

            String genChart = aiData[1].trim();
            if (StringUtils.isBlank(genChart)) {
                // 内容生成错误，拒绝消息
                channel.basicNack(deliveryTag, false, false);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI生成错误");
            }
            // 判断是否包含有双引号，是否符合JSON格式
            boolean flag = genChart.substring(0, 10).chars()
                    .mapToObj(c -> (char) c)
                    .anyMatch(c -> c == '"');
            if (!flag) {
                // 内容生成错误，拒绝消息
                channel.basicNack(deliveryTag, false, false);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI生成错误");
            }

            String genResult = aiData[2].trim();
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            updateChartResult.setStatus(ChartStatusEnum.SUCCEED.getValue());
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                channel.basicNack(deliveryTag, false, false);
                chartService.handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新图表成功状态失败");
            }
            // 手动确认
            channel.basicAck(deliveryTag, false);
            chartService.handleChartUpdate(chart.getId(), "", ChartStatusEnum.SUCCEED.getValue());
        } catch (RetryException e) { // 重试次数超过阈值或被强制中断
            chartService.handleChartUpdateError(chart.getId(), "3次重试后，图表生成失败！！");
            channel.basicReject(deliveryTag, false);
        } catch (ExecutionException e) { // 重试次数超过阈值或被强制中断
            chartService.handleChartUpdateError(chart.getId(), e.getMessage());
            channel.basicReject(deliveryTag, false);
        }
    }

    /**
     * 构建用户输入
     *
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
