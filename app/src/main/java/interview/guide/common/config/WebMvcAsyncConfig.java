package interview.guide.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 异步请求线程池配置。
 * 避免流式接口回退到默认的 SimpleAsyncTaskExecutor。
 */
@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    public static final String WEB_MVC_ASYNC_TASK_EXECUTOR = "webMvcAsyncTaskExecutor";

    private final AsyncTaskExecutor webMvcAsyncTaskExecutor;

    public WebMvcAsyncConfig(
        @Qualifier(WEB_MVC_ASYNC_TASK_EXECUTOR) AsyncTaskExecutor webMvcAsyncTaskExecutor
    ) {
        this.webMvcAsyncTaskExecutor = webMvcAsyncTaskExecutor;
    }

    @Bean(name = WEB_MVC_ASYNC_TASK_EXECUTOR)
    public AsyncTaskExecutor webMvcAsyncTaskExecutor(
        @Value("${app.webmvc.async.pool-size:8}") int poolSize,
        @Value("${app.webmvc.async.queue-capacity:256}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("web-mvc-async-");
        executor.setCorePoolSize(Math.max(1, poolSize));
        executor.setMaxPoolSize(Math.max(1, poolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(webMvcAsyncTaskExecutor);
    }
}
