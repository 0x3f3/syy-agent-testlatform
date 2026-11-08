package com.syy.testplatform.test.executor;
import java.util.List;
import java.util.Map;
public record HttpExecutionResult(String url,String method,int statusCode,Map<String,List<String>> headers,String body,long durationMs,String transportError){}
