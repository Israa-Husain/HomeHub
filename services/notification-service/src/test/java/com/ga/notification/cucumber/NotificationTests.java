package com.ga.notification.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/events.feature")
@ConfigurationParameter(key="cucumber.glue", value="com.ga.notification.cucumber")
@ConfigurationParameter(key="cucumber.plugin", value="pretty,html:target/cucumber-report.html,json:target/cucumber-report.json,junit:target/cucumber-report.xml")
@ConfigurationParameter(key="cucumber.execution.parallel.enabled", value="false")
public class NotificationTests{
}