package com.syy.testplatform.ai.test.parser;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import java.util.List;
public record ParsedOpenApi(String title,String version,String specificationVersion,String serverUrl,String rawDocument,List<ApiDefinition> apis){}
