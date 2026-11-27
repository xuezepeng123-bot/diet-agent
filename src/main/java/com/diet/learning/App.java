package com.diet.learning;

import com.diet.search.MealSearchServer;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;//自动配置

@SpringBootApplication
public class App {
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0 && "--meal-search-server".equals(args[0])) {
            MealSearchServer.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(App.class, args);
    }
}
