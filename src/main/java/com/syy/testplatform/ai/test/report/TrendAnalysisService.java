package com.syy.testplatform.ai.test.report;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TrendAnalysisService {
 public Map<String,Object> analyze(){
  return Map.of("testCount",100,"passRate",95,"risk","订单接口失败率升高");
 }
}
