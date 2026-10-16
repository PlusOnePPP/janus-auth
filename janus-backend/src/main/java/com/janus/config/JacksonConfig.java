package com.janus.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置：Long → String（契约 §8.9，避免超出 JS 安全整数精度丢失）。
 *
 * @author ppp
 */
@Configuration
public class JacksonConfig {

    /** Boot 3 自动注册所有 Jackson Module Bean */
    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
