package com.example.config;

import com.example.utils.Const;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：邮件队列 + 死信队列。
 *
 * 拓扑：
 *   producer ──► mail 队列 ──(消费失败 / 3 分钟未消费)──► dlx.direct ──► error 队列
 *
 * Boot 4 用的是 Jackson 3（tools.jackson），所以这里必须用
 * {@link JacksonJsonMessageConverter}。
 * 参考项目里的 Jackson2JsonMessageConverter 属于 Jackson 2 时代，
 * 在 Boot 4 下即使能编译，也会和默认 ObjectMapper 不是同一套，属于隐性踩坑。
 */
@Configuration
public class RabbitConfiguration {

    /**
     * JSON 消息转换器。
     *
     * 不再用 SimpleMessageConverter 走 Java 序列化：那条路要求消息体 Serializable，
     * 还得维护 addAllowedListPatterns 白名单，且序列化结果不可读、跨语言不友好。
     * JSON 化之后，消息在管理页面里就是肉眼可读的一段文本，排查问题容易得多。
     */
    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        DefaultJacksonJavaTypeMapper mapper = new DefaultJacksonJavaTypeMapper();
        // 反序列化白名单：只信任自己的 DTO 包，防止「消息头里带任意类名」的反序列化攻击
        mapper.setTrustedPackages("java.util", "java.lang", "com.example.entity.dto");
        converter.setJavaTypeMapper(mapper);
        return converter;
    }

    /** 死信队列：保留 24 小时，给人工排查留时间 */
    @Bean("errorQueue")
    public Queue errorQueue() {
        return QueueBuilder
                .durable(Const.MQ_ERROR)
                .ttl(24 * 60 * 60 * 1000)
                .build();
    }

    @Bean("errorExchange")
    public Exchange errorExchange() {
        return ExchangeBuilder
                .directExchange("dlx.direct")
                .durable(true)
                .build();
    }

    @Bean
    public Binding errorBinding(@Qualifier("errorExchange") Exchange exchange,
                                @Qualifier("errorQueue") Queue queue) {
        return BindingBuilder
                .bind(queue)
                .to(exchange)
                .with("error-message")
                .noargs();
    }

    /**
     * 邮件队列。
     * TTL 3 分钟：消息躺过 3 分钟还没被消费（比如消费者进程挂了），自动转投死信交换机。
     */
    @Bean("mailQueue")
    public Queue mailQueue() {
        return QueueBuilder
                .durable(Const.MQ_MAIL)
                .deadLetterExchange("dlx.direct")
                .deadLetterRoutingKey("error-message")
                .ttl(3 * 60 * 1000)
                .build();
    }
}
