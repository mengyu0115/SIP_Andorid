package com.sip.server.config;

/*
 * SWAGGER已临时禁用 - 由于Springfox 3.0.0与Spring Boot 2.7.18+Actuator存在兼容性问题
 *
 * 如需启用API文档，推荐使用SpringDoc OpenAPI替代Springfox
 * 添加依赖: org.springdoc:springdoc-openapi-ui:1.7.0
 * 访问: http://10.29.209.85:8081/swagger-ui.html
 */

/*
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
*/

/**
 * Swagger 配置类 (已禁用)
 *
 * @author SIP Team
 * @version 1.0
 */
//@Configuration
public class SwaggerConfig /* implements WebMvcConfigurer */ {

    /*
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sip.server.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("SIP 即时通信系统 API 文档")
                .description("SIP 会议模块 REST API 接口文档")
                .version("1.0")
                .contact(new Contact("SIP Team", "", ""))
                .build();
    }
    */
}
