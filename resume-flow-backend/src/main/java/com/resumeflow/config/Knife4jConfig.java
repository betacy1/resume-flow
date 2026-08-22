package com.resumeflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ResumeFlow API 文档")
                        .description("网申简历智能填表助手 - 后端接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ResumeFlow")
                                .url("https://github.com/resumeflow")));
    }
}
