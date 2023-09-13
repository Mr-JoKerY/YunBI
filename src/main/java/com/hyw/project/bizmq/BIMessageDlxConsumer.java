package com.hyw.project.bizmq;

import com.hyw.project.common.ErrorCode;
import com.hyw.project.constant.BiMqConstant;
import com.hyw.project.exception.BusinessException;
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
 * bi 死信队列消费者
 *
 * @author hyw
 */
@Slf4j
@Component
public class BIMessageDlxConsumer {

    @Resource
    private ChartService chartService;

    //指定程序监听的消息队列和确认机制
    @RabbitListener(queues = {BiMqConstant.BI_DLX_QUEUE_NAME}, ackMode = "MANUAL")
    public void biReceiveDlxMessage(String message, Channel channel, @Header(value = AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("biReceiveDlxMessage message = {} deliveryTag = {}", message, deliveryTag);
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

            // 如果图表状态已经为 FAILED 直接确认消息 （防止重复更新）
            if (ChartStatusEnum.FAILED.getValue().intValue() == chart.getStatus().intValue()) {
                // 确认消息
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 修改图表状态为 FAILED
            boolean updateRes = chartService.handleUpdateChartStatus(chartId, ChartStatusEnum.FAILED.getValue());
            if (!updateRes) {
                log.info("处理死信队列消息失败, 失败图表id = {}", chart.getId());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }

            // 确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("其他异常 error = {}", e.getMessage());
            }
            log.error("任务处理失败 message = {} deliveryTag = {} error = {}", message, deliveryTag, e.getMessage());
        }
    }
}