package com.syy.testplatform.ai.test.report;
import com.syy.testplatform.ai.test.entity.*;
import com.syy.testplatform.ai.test.repository.*;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class TestReportService {
 private final TestReportRepository reportRepository;private final TestExecutionRepository executionRepository;private final AgentTestTaskRepository taskRepository;private final ApiDefinitionRepository apiRepository;
 public TestReportService(TestReportRepository reportRepository,TestExecutionRepository executionRepository,AgentTestTaskRepository taskRepository,ApiDefinitionRepository apiRepository){this.reportRepository=reportRepository;this.executionRepository=executionRepository;this.taskRepository=taskRepository;this.apiRepository=apiRepository;}
 public Map<String,Object> trend(){List<TestReport> reports=reportRepository.findAllByOrderByCreateTimeAsc();if(reports.size()>30)reports=reports.subList(reports.size()-30,reports.size());DateTimeFormatter fmt=DateTimeFormatter.ofPattern("MM-dd HH:mm");List<String> dates=new ArrayList<>();List<Double> rates=new ArrayList<>();for(TestReport r:reports){dates.add(r.getCreateTime()==null?"":r.getCreateTime().format(fmt));rates.add(r.getPassRate()==null?0:r.getPassRate());}return Map.of("dates",dates,"passRate",rates);}
 public Map<String,Object> quality(){List<TestExecution> executions=executionRepository.findAllByOrderByExecuteTimeDesc();Map<Long,String> paths=new HashMap<>();Map<String,Integer> failed=new LinkedHashMap<>();for(TestExecution e:executions){if("PASS".equals(e.getStatus()))continue;String path=paths.computeIfAbsent(e.getApiId(),id->apiRepository.findById(id).map(a->a.getMethod()+" "+a.getPath()).orElse("API #"+id));failed.merge(path,1,Integer::sum);}List<Map.Entry<String,Integer>> entries=new ArrayList<>(failed.entrySet());entries.sort(Map.Entry.<String,Integer>comparingByValue().reversed());if(entries.size()>10)entries=entries.subList(0,10);return Map.of("interfaces",entries.stream().map(Map.Entry::getKey).toList(),"failed",entries.stream().map(Map.Entry::getValue).toList());}
 public Map<String,Object> summary(){List<TestTask> tasks=taskRepository.findAllByOrderByCreatedAtDesc();int total=tasks.stream().mapToInt(t->safe(t.getTotalCount())).sum();int passed=tasks.stream().mapToInt(t->safe(t.getPassedCount())).sum();int failed=tasks.stream().mapToInt(t->safe(t.getFailedCount())).sum();int errors=tasks.stream().mapToInt(t->safe(t.getErrorCount())).sum();double rate=total==0?0:Math.round(passed*10000.0/total)/100.0;return Map.of("taskCount",tasks.size(),"totalCount",total,"passedCount",passed,"failedCount",failed,"errorCount",errors,"passRate",rate);}
 public Optional<TestReport> report(Long id){return reportRepository.findById(id);}
 private int safe(Integer value){return value==null?0:value;}
}
