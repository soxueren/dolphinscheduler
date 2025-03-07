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

package org.apache.dolphinscheduler.e2e.cases;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.dolphinscheduler.e2e.core.DolphinScheduler;
import org.apache.dolphinscheduler.e2e.core.WebDriverWaitFactory;
import org.apache.dolphinscheduler.e2e.pages.LoginPage;
import org.apache.dolphinscheduler.e2e.pages.common.NavBarPage;
import org.apache.dolphinscheduler.e2e.pages.project.ProjectDetailPage;
import org.apache.dolphinscheduler.e2e.pages.project.ProjectPage;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowDefinitionTab;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowForm;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowInstanceTab;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.task.HttpTaskForm;
import org.apache.dolphinscheduler.e2e.pages.security.SecurityPage;
import org.apache.dolphinscheduler.e2e.pages.security.TenantPage;
import org.apache.dolphinscheduler.e2e.pages.security.UserPage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.testcontainers.shaded.org.awaitility.Awaitility;
@DolphinScheduler(composeFiles = "docker/workflow-http/docker-compose.yaml")
public class WorkflowHttpTaskE2ETest {

    private static final String project = "test-workflow-1";

    private static final String workflow = "test-workflow-1";

    private static final String user = "admin";

    private static final String password = "dolphinscheduler123";

    private static final String email = "admin@gmail.com";

    private static final String phone = "15800000000";

    private static final String tenant = System.getProperty("user.name");

    private static final String mockServerUrl = "http://mockServer:1080/test";

    private static RemoteWebDriver browser;

    @BeforeAll
    public static void setup() {
        UserPage userPage = new LoginPage(browser)
                .login("admin", "dolphinscheduler123")
                .goToNav(SecurityPage.class)
                .goToTab(TenantPage.class)
                .create(tenant)
                .goToNav(SecurityPage.class)
                .goToTab(UserPage.class);

        WebDriverWaitFactory.createWebDriverWait(userPage.driver()).until(ExpectedConditions.visibilityOfElementLocated(
                new By.ByClassName("name")));

        userPage.update(user, user, email, phone, tenant)
                .goToNav(ProjectPage.class)
                .create(project);
    }

    @AfterAll
    public static void cleanup() {
        new NavBarPage(browser)
                .goToNav(ProjectPage.class)
                .goTo(project)
                .goToTab(WorkflowDefinitionTab.class)
                .delete(workflow);

        new NavBarPage(browser)
                .goToNav(ProjectPage.class)
                .delete(project);

        browser.navigate().refresh();

        new NavBarPage(browser)
                .goToNav(SecurityPage.class)
                .goToTab(TenantPage.class)
                .delete(tenant);
    }

    @Test
    @Order(1)
    void testCreateWorkflow() {
        WorkflowDefinitionTab workflowDefinitionPage =
                new ProjectPage(browser)
                        .goTo(project)
                        .goToTab(WorkflowDefinitionTab.class);

        workflowDefinitionPage
                .createWorkflow()
                .<HttpTaskForm>addTask(WorkflowForm.TaskType.HTTP)
                .url(mockServerUrl)
                .name("test-1")
                .addParam("today", "${system.datetime}")
                .submit()

                .submit()
                .name(workflow)
                .addGlobalParam("global_param", "hello world")
                .submit();

        Awaitility.await().untilAsserted(() -> assertThat(workflowDefinitionPage.workflowList())
                .as("Workflow list should contain newly-created workflow")
                .anyMatch(
                        it -> it.getText().contains(workflow)));
        workflowDefinitionPage.publish(workflow);
    }

    @Test
    @Order(30)
    void testRunWorkflow() {
        final ProjectDetailPage projectPage =
                new ProjectPage(browser)
                        .goToNav(ProjectPage.class)
                        .goTo(project);

        projectPage
                .goToTab(WorkflowInstanceTab.class)
                .deleteAll();
        projectPage
                .goToTab(WorkflowDefinitionTab.class)
                .run(workflow)
                .submit();

        Awaitility.await().untilAsserted(() -> {
            browser.navigate().refresh();

            final WorkflowInstanceTab.Row row = projectPage
                    .goToTab(WorkflowInstanceTab.class)
                    .instances()
                    .iterator()
                    .next();

            assertThat(row.isSuccess()).isTrue();
            assertThat(row.executionTime()).isEqualTo(1);
        });
    }
}
