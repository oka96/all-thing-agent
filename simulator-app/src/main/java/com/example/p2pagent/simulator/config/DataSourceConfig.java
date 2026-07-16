package com.example.p2pagent.simulator.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    @Primary
    public HikariDataSource appDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("p2p-writer");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(8);
        return dataSource;
    }

    @Bean(destroyMethod = "close", name = "diagnosticDataSource")
    public HikariDataSource diagnosticDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${app.diagnostics.username}") String username,
            @Value("${app.diagnostics.password}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("p2p-diagnostics-read-only");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(3);
        dataSource.setConnectionInitSql("SET QUERY_TIMEOUT 3000");
        return dataSource;
    }

    @Bean
    @Primary
    public JdbcClient appJdbcClient(@Qualifier("appDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean(name = "diagnosticJdbcClient")
    public JdbcClient diagnosticJdbcClient(@Qualifier("diagnosticDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager appTransactionManager(
            @Qualifier("appDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}

