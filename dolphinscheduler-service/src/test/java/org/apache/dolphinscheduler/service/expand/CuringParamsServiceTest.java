/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.service.expand;

import org.apache.dolphinscheduler.common.enums.CommandType;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.ProcessDefinition;
import org.apache.dolphinscheduler.dao.entity.ProcessInstance;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.mapper.ProjectParameterMapper;
import org.apache.dolphinscheduler.extract.master.command.BackfillWorkflowCommandParam;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.enums.DataType;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.SubProcessParameters;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Lists;

@ExtendWith(MockitoExtension.class)
public class CuringParamsServiceTest {

    private static final String placeHolderName = "$[yyyy-MM-dd-1]";

    @Mock
    private CuringParamsService curingGlobalParamsService;

    @InjectMocks
    private CuringParamsServiceImpl dolphinSchedulerCuringGlobalParams;

    @Mock
    private TimePlaceholderResolverExpandService timePlaceholderResolverExpandService;

    @Mock
    private ProjectParameterMapper projectParameterMapper;

    @InjectMocks
    private TimePlaceholderResolverExpandServiceImpl timePlaceholderResolverExpandServiceImpl;

    private final Map<String, String> globalParamMap = new HashMap<>();
    private final Map<String, Property> paramMap = new HashMap<>();

    @BeforeEach
    public void init() {
        globalParamMap.put("globalParams1", "Params1");
        paramMap.put("globalParams1", new Property("globalParams1", Direct.IN, DataType.VARCHAR, "Params1"));
    }

    @Test
    public void testConvertParameterPlaceholders() {
        Mockito.when(curingGlobalParamsService.convertParameterPlaceholders(placeHolderName, paramMap))
                .thenReturn("2022-06-26");
        String result = curingGlobalParamsService.convertParameterPlaceholders(placeHolderName, paramMap);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testTimeFunctionNeedExpand() {
        boolean result = curingGlobalParamsService.timeFunctionNeedExpand(placeHolderName);
        Assertions.assertFalse(result);
    }

    @Test
    public void testTimeFunctionExtension() {
        String result = curingGlobalParamsService.timeFunctionExtension(1, "", placeHolderName);
        Assertions.assertNull(result);
    }

    @Test
    public void testCuringGlobalParams() {
        // define globalMap
        Map<String, String> globalParamMap = new HashMap<>();
        globalParamMap.put("globalParams1", "Params1");

        // define globalParamList
        List<Property> globalParamList = new ArrayList<>();

        // define scheduleTime
        Date scheduleTime = DateUtils.stringToDate("2019-12-20 00:00:00");

        // test globalParamList is null
        String result = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null);
        Assertions.assertNull(result);
        Assertions.assertNull(dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, null, null,
                CommandType.START_CURRENT_TASK_PROCESS, null, null));
        Assertions.assertNull(dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, null,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null));

        // test globalParamList is not null
        Property property = new Property("testGlobalParam", Direct.IN, DataType.VARCHAR, "testGlobalParam");
        globalParamList.add(property);

        String result2 = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, null, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null);
        Assertions.assertEquals(result2, JSONUtils.toJsonString(globalParamList));

        String result3 = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, null, null);
        Assertions.assertEquals(result3, JSONUtils.toJsonString(globalParamList));

        String result4 = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null);
        Assertions.assertEquals(result4, JSONUtils.toJsonString(globalParamList));

        // test var $ startsWith
        globalParamMap.put("bizDate", "${system.biz.date}");
        globalParamMap.put("b1zCurdate", "${system.biz.curdate}");

        Property property2 = new Property("testParamList1", Direct.IN, DataType.VARCHAR, "testParamList");
        Property property3 = new Property("testParamList2", Direct.IN, DataType.VARCHAR, "{testParamList1}");
        Property property4 = new Property("testParamList3", Direct.IN, DataType.VARCHAR, "${b1zCurdate}");

        globalParamList.add(property2);
        globalParamList.add(property3);
        globalParamList.add(property4);

        String result5 = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null);
        Assertions.assertEquals(result5, JSONUtils.toJsonString(globalParamList));

        Property testStartParamProperty = new Property("testStartParam", Direct.IN, DataType.VARCHAR, "");
        globalParamList.add(testStartParamProperty);
        Property testStartParam2Property =
                new Property("testStartParam2", Direct.IN, DataType.VARCHAR, "$[yyyy-MM-dd+1]");
        globalParamList.add(testStartParam2Property);
        globalParamMap.put("testStartParam", "");
        globalParamMap.put("testStartParam2", "$[yyyy-MM-dd+1]");

        Map<String, String> startParamMap = new HashMap<>(2);
        startParamMap.put("testStartParam", "$[yyyyMMdd]");

        for (Map.Entry<String, String> param : globalParamMap.entrySet()) {
            String val = startParamMap.get(param.getKey());
            if (val != null) {
                param.setValue(val);
            }
        }

        String result6 = dolphinSchedulerCuringGlobalParams.curingGlobalParams(1, globalParamMap, globalParamList,
                CommandType.START_CURRENT_TASK_PROCESS, scheduleTime, null);
        Assertions.assertEquals(result6, JSONUtils.toJsonString(globalParamList));
    }

    @Test
    public void testParamParsingPreparation() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setExecutePath("home/path/execute");

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setName("TaskName-1");
        taskDefinition.setCode(1000001l);

        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(2);
        final BackfillWorkflowCommandParam backfillWorkflowCommandParam = BackfillWorkflowCommandParam.builder()
                .timeZone("Asia/Shanghai")
                .build();
        processInstance.setCommandParam(JSONUtils.toJsonString(backfillWorkflowCommandParam));
        processInstance.setHistoryCmd(CommandType.COMPLEMENT_DATA.toString());
        Property property = new Property();
        property.setDirect(Direct.IN);
        property.setProp("global_params");
        property.setValue("hello world");
        property.setType(DataType.VARCHAR);
        List<Property> properties = Lists.newArrayList(property);
        processInstance.setGlobalParams(JSONUtils.toJsonString(properties));

        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setName("ProcessName-1");
        processDefinition.setProjectName("ProjectName-1");
        processDefinition.setProjectCode(3000001L);
        processDefinition.setCode(200001L);

        processInstance.setProcessDefinitionCode(processDefinition.getCode());
        processInstance.setProjectCode(processDefinition.getProjectCode());
        taskInstance.setTaskCode(taskDefinition.getCode());
        taskInstance.setTaskDefinitionVersion(taskDefinition.getVersion());
        taskInstance.setProjectCode(processDefinition.getProjectCode());
        taskInstance.setProcessInstanceId(processInstance.getId());

        AbstractParameters parameters = new SubProcessParameters();

        Mockito.when(projectParameterMapper.queryByProjectCode(Mockito.anyLong())).thenReturn(Collections.emptyList());

        Map<String, Property> propertyMap =
                dolphinSchedulerCuringGlobalParams.paramParsingPreparation(taskInstance, parameters, processInstance);
        Assertions.assertNotNull(propertyMap);
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_TASK_INSTANCE_ID).getValue(),
                String.valueOf(taskInstance.getId()));
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_TASK_EXECUTE_PATH).getValue(),
                taskInstance.getExecutePath());
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_WORKFLOW_INSTANCE_ID).getValue(),
                String.valueOf(processInstance.getId()));
        // Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_WORKFLOW_DEFINITION_NAME).getValue(),
        // processDefinition.getName());
        // Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_PROJECT_NAME).getValue(),
        // processDefinition.getProjectName());
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_PROJECT_CODE).getValue(),
                String.valueOf(processDefinition.getProjectCode()));
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_TASK_DEFINITION_CODE).getValue(),
                String.valueOf(taskDefinition.getCode()));
        Assertions.assertEquals(propertyMap.get(TaskConstants.PARAMETER_WORKFLOW_DEFINITION_CODE).getValue(),
                String.valueOf(processDefinition.getCode()));
    }

    @Test
    public void testParseWorkflowStartParam() {
        Map<String, Property> result = new HashMap<>();
        // empty cmd param
        Map<String, String> startParamMap = new HashMap<>();
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowStartParam(startParamMap);
        Assertions.assertTrue(MapUtils.isEmpty(result));

        // without key
        startParamMap.put("testStartParam", "$[yyyyMMdd]");
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowStartParam(startParamMap);
        Assertions.assertTrue(MapUtils.isEmpty(result));

        startParamMap.put("StartParams", "{\"param1\":\"11111\", \"param2\":\"22222\"}");
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowStartParam(startParamMap);
        Assertions.assertTrue(MapUtils.isNotEmpty(result));
        Assertions.assertEquals(2, result.keySet().size());
        Assertions.assertEquals("11111", result.get("param1").getValue());
        Assertions.assertEquals("22222", result.get("param2").getValue());
    }

    @Test
    public void testParseWorkflowFatherParam() {
        Map<String, Property> result = new HashMap<>();
        // empty cmd param
        Map<String, String> startParamMap = new HashMap<>();
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowFatherParam(startParamMap);
        Assertions.assertTrue(MapUtils.isEmpty(result));

        // without key
        startParamMap.put("testfatherParams", "$[yyyyMMdd]");
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowFatherParam(startParamMap);
        Assertions.assertTrue(MapUtils.isEmpty(result));

        startParamMap.put("fatherParams", "{\"param1\":\"11111\", \"param2\":\"22222\"}");
        result = dolphinSchedulerCuringGlobalParams.parseWorkflowFatherParam(startParamMap);
        Assertions.assertTrue(MapUtils.isNotEmpty(result));
        Assertions.assertEquals(2, result.keySet().size());
        Assertions.assertEquals("11111", result.get("param1").getValue());
        Assertions.assertEquals("22222", result.get("param2").getValue());
    }
}
