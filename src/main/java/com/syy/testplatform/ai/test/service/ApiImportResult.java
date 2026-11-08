package com.syy.testplatform.ai.test.service;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import java.util.List;
public record ApiImportResult(ApiDocument document,List<ApiDefinition> apis,int apiCount,String ragIndexStatus){}
