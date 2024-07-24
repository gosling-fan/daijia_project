package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "wx.miniapp")
//读取以wx.miniapp为前缀的配置文件中的属性值
public class WxConfigProperties {
    private String appId;
    private String secret;
}
