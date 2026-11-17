package com.ga.auth.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key="cucumber.glue",value="com.ga.auth.cucumber")
@ConfigurationParameter(key="cucumber.plugin",value="pretty,html:target/cucumber-report.html,json:target/cucumber-report.json,junit:target/cucumber-report.xml")
@ConfigurationParameter(key="cucumber.execution.parallel.enabled",value="false")
public class RunCucumberTest{
}
