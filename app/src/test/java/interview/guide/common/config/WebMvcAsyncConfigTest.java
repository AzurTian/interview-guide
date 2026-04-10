package interview.guide.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMvcAsyncConfigTest {

    @Test
    void webMvcAsyncTaskExecutorUsesConfiguredThreadPoolSettings() {
        WebMvcAsyncConfig config = new WebMvcAsyncConfig(createExecutor());

        AsyncTaskExecutor executor = config.webMvcAsyncTaskExecutor(6, 128);

        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(6, threadPoolTaskExecutor.getCorePoolSize());
        assertEquals(6, threadPoolTaskExecutor.getMaxPoolSize());
        assertEquals(128, threadPoolTaskExecutor.getQueueCapacity());
    }

    @Test
    void configureAsyncSupportRegistersDedicatedTaskExecutor() {
        AsyncTaskExecutor executor = createExecutor();
        WebMvcAsyncConfig config = new WebMvcAsyncConfig(executor);
        InspectableAsyncSupportConfigurer configurer = new InspectableAsyncSupportConfigurer();

        config.configureAsyncSupport(configurer);

        assertSame(executor, configurer.taskExecutor());
    }

    private ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("test-web-mvc-async-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        return executor;
    }

    private static final class InspectableAsyncSupportConfigurer extends AsyncSupportConfigurer {

        private AsyncTaskExecutor taskExecutor() {
            return getTaskExecutor();
        }
    }
}
