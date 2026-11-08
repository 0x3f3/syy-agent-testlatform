package com.syy.testplatform.ai.test.service;
import java.util.List;
public record CreateTestTaskRequest(Long documentId,String taskName,String baseUrl,List<Long> caseIds){}
