package com.syy.testplatform.ai.test.service;
import com.syy.testplatform.ai.test.entity.TestExecution;
import com.syy.testplatform.ai.test.entity.TestReport;
import com.syy.testplatform.ai.test.entity.TestTask;
import java.util.List;
public record TaskRunResponse(TestTask task,List<TestExecution> executions,TestReport report){}
