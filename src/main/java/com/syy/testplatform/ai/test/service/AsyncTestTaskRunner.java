package com.syy.testplatform.ai.test.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsyncTestTaskRunner {
    private static final Logger log = LoggerFactory.getLogger(AsyncTestTaskRunner.class);
    private final TestTaskService taskService;

    @Async("testTaskExecutor")
    public void run(Long taskId) {
        try {
            taskService.executeSubmittedTask(taskId);
        } catch (Exception ex) {
            log.error("异步测试任务执行失败，taskId={}", taskId, ex);
        }
    }
}
