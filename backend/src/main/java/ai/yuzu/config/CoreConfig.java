package ai.yuzu.config;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.concurrent.ErrorSink;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** v0.0.1 🍊 Core infrastructure beans: clock, natural time, virtual-thread executor, timer, async runner. */
@Configuration
public class CoreConfig {

    /** v0.0.1 🍊 System clock (tests replace it with a fixed clock). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** v0.0.1 🍊 Natural-language time renderer bound to the workgroup zone. */
    @Bean
    public NaturalTime naturalTime(Clock clock, YuzuProperties properties) {
        return new NaturalTime(clock, properties.zone());
    }

    /** v0.0.1 🍊 One virtual thread per task; used for agent loops, module calls and tools. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService virtualExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("yuzu-v-", 0).factory());
    }

    /** v0.0.1 🍊 Single platform thread used only for timers (debounce, throttle, heartbeat). */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService timerExecutor() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("yuzu-timer").daemon(true).factory());
    }

    /** v0.0.1 🍊 Guarded async runner that reports every background failure to all error sinks. */
    @Bean
    public AsyncRunner asyncRunner(ExecutorService virtualExecutor, List<ErrorSink> sinks) {
        return new AsyncRunner(virtualExecutor, sinks);
    }
}
