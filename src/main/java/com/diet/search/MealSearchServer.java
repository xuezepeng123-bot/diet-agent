package com.diet.search;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.net.InetSocketAddress;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@MapperScan(basePackageClasses = MealMapper.class, annotationClass = Mapper.class)
public class MealSearchServer {
    public static void run(String[] args) throws InterruptedException {
        try (var context = new SpringApplicationBuilder(MealSearchServer.class)
                .web(WebApplicationType.NONE).run(args)) {
            context.getBean(Server.class).awaitTermination();
        }
    }

    @Bean(initMethod = "start", destroyMethod = "shutdownNow")
    Server mealSearchGrpcServer(MealMapper mapper,
            @Value("${diet.meal-search.host:127.0.0.1}") String host,
            @Value("${diet.meal-search.port:50051}") int port) {
        return NettyServerBuilder.forAddress(new InetSocketAddress(host, port))
                .addService(new MealLookupEndpoint(mapper))
                .build();
    }
}
