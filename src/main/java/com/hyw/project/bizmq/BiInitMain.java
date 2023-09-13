package com.hyw.project.bizmq;

import com.hyw.project.constant.BiMqConstant;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
@Slf4j
public class BiInitMain {

    public static void doInit() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection();
            Channel channel = connection.createChannel()) {

            // 创建死信交换机
            channel.exchangeDeclare(BiMqConstant.BI_DLX_EXCHANGE_NAME, "direct");
            // 创建死信队列
            channel.queueDeclare(BiMqConstant.BI_DLX_QUEUE_NAME, true, false, false, null);
            channel.queueBind(BiMqConstant.BI_DLX_QUEUE_NAME, BiMqConstant.BI_DLX_EXCHANGE_NAME, BiMqConstant.BI_DLX_ROUTING_KEY);

            channel.exchangeDeclare(BiMqConstant.BI_EXCHANGE_NAME, "direct");
            // 指定死信队列参数
            Map<String, Object> args = new HashMap<>(2);
            // 要绑定那个死信交换机
            args.put("x-dead-letter-exchange", BiMqConstant.BI_DLX_EXCHANGE_NAME);
            // 指定死信要发送到那个死信队列
            args.put("x-dead-letter-routing-key", BiMqConstant.BI_DLX_ROUTING_KEY);
            channel.queueDeclare(BiMqConstant.BI_QUEUE_NAME, true, false, false, args);
            channel.queueBind(BiMqConstant.BI_QUEUE_NAME, BiMqConstant.BI_EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY);

//            // 创建重试交换机(要与以上普通队列分开创建)
//            channel.exchangeDeclare(BiMqConstant.BI_RELOAD_EXCHANGE_NAME, "direct");
//            // 创建重试队列,并绑定死信交换机
//            channel.queueDeclare(BiMqConstant.BI_RELOAD_QUEUE_NAME, true, false, false, args);
//            channel.queueBind(BiMqConstant.BI_RELOAD_QUEUE_NAME, BiMqConstant.BI_RELOAD_EXCHANGE_NAME, BiMqConstant.BI_RELOAD_ROUTING_KEY);
            log.info("消息队列启动成功");
        } catch (Exception e) {
            log.error("消息队列启动失败");
        }
    }

    public static void main(String[] args) {
        doInit();
    }
}
