package com.syy.testplatform.test.report;

import org.springframework.stereotype.Component;

@Component
public class MarkdownReportGenerator {
    public String generate(int total,int success,String aiAnalysis){
        double rate=total==0?0:(success*100.0/total);
        return "# AI接口自动化测试报告\n\n"+
                "测试数量: "+total+"\n\n"+
                "通过数量: "+success+"\n\n"+
                "通过率: "+rate+"%\n\n"+
                "## AI分析\n"+aiAnalysis;
    }
}
