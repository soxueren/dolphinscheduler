/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.dolphinscheduler.api.test.cases;

import org.apache.dolphinscheduler.api.test.core.DolphinScheduler;
import org.apache.dolphinscheduler.api.test.entity.HttpResponse;
import org.apache.dolphinscheduler.api.test.entity.LoginResponseData;
import org.apache.dolphinscheduler.api.test.pages.LoginPage;
import org.apache.dolphinscheduler.api.test.pages.project.ProjectPage;
import org.apache.dolphinscheduler.api.test.pages.workflow.ExecutorPage;
import org.apache.dolphinscheduler.api.test.pages.workflow.ProcessDefinitionPage;
import org.apache.dolphinscheduler.api.test.utils.JSONUtils;
import org.apache.dolphinscheduler.common.enums.FailureStrategy;
import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.common.enums.UserType;
import org.apache.dolphinscheduler.common.enums.WarningType;
import org.apache.dolphinscheduler.dao.entity.User;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

//TODO: Some test cases rely on ProcessInstance APIs. Should complete remaining cases after ProcessInstance related API tests done.
@DolphinScheduler(composeFiles = "docker/basic/docker-compose.yaml")
@Slf4j
public class ExecutorAPITest {

    private static final String username = "admin";

    private static final String password = "dolphinscheduler123";

    private static String sessionId;

    private static User loginUser;

    private static ExecutorPage executorPage;

    private static ProcessDefinitionPage processDefinitionPage;

    private static ProjectPage projectPage;

    private static long projectCode;

    private static long processDefinitionCode;

    private static long triggerCode;

    @BeforeAll
    public static void setup() {
        LoginPage loginPage = new LoginPage();
        HttpResponse loginHttpResponse = loginPage.login(username, password);
        sessionId =
                JSONUtils.convertValue(loginHttpResponse.getBody().getData(), LoginResponseData.class).getSessionId();
        executorPage = new ExecutorPage(sessionId);
        processDefinitionPage = new ProcessDefinitionPage(sessionId);
        projectPage = new ProjectPage(sessionId);
        loginUser = new User();
        loginUser.setUserName("admin");
        loginUser.setId(1);
        loginUser.setUserType(UserType.ADMIN_USER);
    }

    @AfterAll
    public static void cleanup() {
        log.info("success cleanup");
    }

    @Test
    @Order(1)
    public void testStartProcessInstance() {
        try {
            // create test project
            HttpResponse createProjectResponse = projectPage.createProject(loginUser, "project-test");
            HttpResponse queryAllProjectListResponse = projectPage.queryAllProjectList(loginUser);
            Assertions.assertTrue(queryAllProjectListResponse.getBody().getSuccess());
            projectCode = (long) ((LinkedHashMap<String, Object>) ((List<LinkedHashMap>) queryAllProjectListResponse
                    .getBody().getData()).get(0)).get("code");

            // upload test workflow definition json
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("workflow-json/test.json").getFile());
            CloseableHttpResponse importProcessDefinitionResponse = processDefinitionPage
                    .importProcessDefinition(loginUser, projectCode, file);
            String data = EntityUtils.toString(importProcessDefinitionResponse.getEntity());
            Assertions.assertTrue(data.contains("\"success\":true"));

            // get workflow definition code
            HttpResponse queryAllProcessDefinitionByProjectCodeResponse =
                    processDefinitionPage.queryAllProcessDefinitionByProjectCode(loginUser, projectCode);
            Assertions.assertTrue(queryAllProcessDefinitionByProjectCodeResponse.getBody().getSuccess());
            Assertions.assertTrue(queryAllProcessDefinitionByProjectCodeResponse.getBody().getData().toString()
                    .contains("hello world"));
            processDefinitionCode =
                    (long) ((LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) ((List<LinkedHashMap>) queryAllProcessDefinitionByProjectCodeResponse
                            .getBody().getData()).get(0)).get("processDefinition")).get("code");

            // release test workflow
            HttpResponse releaseProcessDefinitionResponse = processDefinitionPage.releaseProcessDefinition(loginUser,
                    projectCode, processDefinitionCode, ReleaseState.ONLINE);
            Assertions.assertTrue(releaseProcessDefinitionResponse.getBody().getSuccess());

            // trigger workflow instance
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();
            String scheduleTime = String.format("%s,%s", formatter.format(date), formatter.format(date));
            log.info("use current time {} as scheduleTime", scheduleTime);
            HttpResponse startProcessInstanceResponse = executorPage.startProcessInstance(loginUser, projectCode,
                    processDefinitionCode, scheduleTime, FailureStrategy.END, WarningType.NONE);
            Assertions.assertTrue(startProcessInstanceResponse.getBody().getSuccess());

            triggerCode = (long) startProcessInstanceResponse.getBody().getData();
        } catch (Exception e) {
            log.error("failed", e);
            Assertions.fail();
        }
    }

}
