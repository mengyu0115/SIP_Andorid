package com.sip.server.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MyBatis Plus 配置
 *
 */
@Configuration
@MapperScan("com.sip.server.mapper")
public class MyBatisPlusConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // 配置类型别名包
        sessionFactory.setTypeAliasesPackage("com.sip.server.entity");

        return sessionFactory.getObject();
    }
}
