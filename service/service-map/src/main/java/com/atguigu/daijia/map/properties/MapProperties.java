package com.atguigu.daijia.map.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("tencent.map")
@Data
public class MapProperties {
    private String key;
}
