package com.syy.testplatform.ai.test.config;
import com.syy.testplatform.test.agent.TestCaseGenerateAgent;
import com.syy.testplatform.ai.agent.AiTestReportAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class TestAgentConfig {
 @Bean
 public TestCaseGenerateAgent testCaseGenerateAgent(@Qualifier("myQwenChatModel") ChatModel chatModel){
  return AiServices.builder(TestCaseGenerateAgent.class).chatModel(chatModel).build();
 }
 @Bean
 public AiTestReportAgent aiTestReportAgent(@Qualifier("myQwenChatModel") ChatModel chatModel){
  return AiServices.builder(AiTestReportAgent.class).chatModel(chatModel).build();
 }
}
