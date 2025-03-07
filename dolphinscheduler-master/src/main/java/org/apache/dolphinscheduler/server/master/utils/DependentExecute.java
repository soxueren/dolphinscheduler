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

package org.apache.dolphinscheduler.server.master.utils;

import static org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters.DependentFailurePolicyEnum.DEPENDENT_FAILURE_WAITING;

import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.TaskExecuteType;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.ProcessInstance;
import org.apache.dolphinscheduler.dao.entity.ProcessTaskRelation;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskDefinitionLog;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.repository.ProcessInstanceDao;
import org.apache.dolphinscheduler.dao.repository.TaskDefinitionDao;
import org.apache.dolphinscheduler.dao.repository.TaskDefinitionLogDao;
import org.apache.dolphinscheduler.dao.repository.TaskInstanceDao;
import org.apache.dolphinscheduler.plugin.task.api.enums.DependResult;
import org.apache.dolphinscheduler.plugin.task.api.enums.DependentRelation;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskExecutionStatus;
import org.apache.dolphinscheduler.plugin.task.api.model.DateInterval;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;
import org.apache.dolphinscheduler.plugin.task.api.utils.DependentUtils;
import org.apache.dolphinscheduler.service.bean.SpringApplicationContext;
import org.apache.dolphinscheduler.service.process.ProcessService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * dependent item execute
 */
@Slf4j
public class DependentExecute {

    private final ProcessInstanceDao processInstanceDao = SpringApplicationContext.getBean(ProcessInstanceDao.class);

    private final TaskInstanceDao taskInstanceDao = SpringApplicationContext.getBean(TaskInstanceDao.class);

    /**
     * depend item list
     */
    private List<DependentItem> dependItemList;

    /**
     * dependent relation
     */
    private DependentRelation relation;

    private ProcessInstance processInstance;

    private TaskInstance taskInstance;

    /**
     * depend result map
     */
    private Map<String, DependResult> dependResultMap = new HashMap<>();

    /**
     * process service
     */
    private final ProcessService processService = SpringApplicationContext.getBean(ProcessService.class);

    /**
     * task definition log dao
     */
    private final TaskDefinitionLogDao taskDefinitionLogDao =
            SpringApplicationContext.getBean(TaskDefinitionLogDao.class);

    /**
     * task definition dao
     */
    private final TaskDefinitionDao taskDefinitionDao = SpringApplicationContext.getBean(TaskDefinitionDao.class);

    private Map<String, Property> dependTaskVarPoolPropertyMap = new HashMap<>();

    private Map<String, Long> dependTaskVarPoolEndTimeMap = new HashMap<>();

    private Map<String, Property> dependItemVarPoolPropertyMap = new HashMap<>();

    private Map<String, Long> dependItemVarPoolEndTimeMap = new HashMap<>();

    /**
     * constructor
     *
     * @param itemList item list
     * @param relation relation
     */
    public DependentExecute(List<DependentItem> itemList, DependentRelation relation, ProcessInstance processInstance,
                            TaskInstance taskInstance) {
        this.dependItemList = itemList;
        this.relation = relation;
        this.processInstance = processInstance;
        this.taskInstance = taskInstance;
    }

    /**
     * get dependent item for one dependent item
     *
     * @param dependentItem dependent item
     * @param currentTime   current time
     * @return DependResult
     */
    private DependResult getDependentResultForItem(DependentItem dependentItem, Date currentTime, int testFlag) {
        List<DateInterval> dateIntervals =
                DependentUtils.getDateIntervalList(currentTime, dependentItem.getDateValue());
        return calculateResultForTasks(dependentItem, dateIntervals, testFlag);
    }

    /**
     * calculate dependent result for one dependent item.
     *
     * @param dependentItem dependent item
     * @param dateIntervals date intervals
     * @return dateIntervals
     */
    private DependResult calculateResultForTasks(DependentItem dependentItem,
                                                 List<DateInterval> dateIntervals,
                                                 int testFlag) {

        DependResult result = DependResult.FAILED;
        for (DateInterval dateInterval : dateIntervals) {
            ProcessInstance processInstance =
                    findLastProcessInterval(dependentItem.getDefinitionCode(), dependentItem.getDepTaskCode(),
                            dateInterval, testFlag);
            if (processInstance == null) {
                return DependResult.WAITING;
            }
            // need to check workflow for updates, so get all task and check the task state
            if (dependentItem.getDepTaskCode() == Constants.DEPENDENT_WORKFLOW_CODE) {
                result = dependResultByProcessInstance(processInstance);
            } else if (dependentItem.getDepTaskCode() == Constants.DEPENDENT_ALL_TASK_CODE) {
                result = dependResultByAllTaskOfProcessInstance(processInstance, testFlag);
            } else {
                result = dependResultBySingleTaskInstance(processInstance, dependentItem.getDepTaskCode(), testFlag);
            }
            if (result != DependResult.SUCCESS) {
                break;
            }
        }
        return result;
    }

    /**
     * depend type = depend_work_flow
     *
     * @return
     */
    private DependResult dependResultByProcessInstance(ProcessInstance processInstance) {
        if (!processInstance.getState().isFinished()) {
            return DependResult.WAITING;
        }
        if (processInstance.getState().isSuccess()) {
            addItemVarPool(processInstance.getVarPool(), processInstance.getEndTime().getTime());
            return DependResult.SUCCESS;
        }
        log.warn(
                "The dependent workflow did not execute successfully, so return depend failed. processCode: {}, processName: {}",
                processInstance.getProcessDefinitionCode(), processInstance.getName());
        return DependResult.FAILED;
    }

    /**
     * depend type = depend_all
     *
     * @return
     */
    private DependResult dependResultByAllTaskOfProcessInstance(ProcessInstance processInstance, int testFlag) {
        if (!processInstance.getState().isFinished()) {
            log.info("Wait for the dependent workflow to complete, processCode: {}, processInstanceId: {}.",
                    processInstance.getProcessDefinitionCode(), processInstance.getId());
            return DependResult.WAITING;
        }
        if (processInstance.getState().isSuccess()) {
            List<ProcessTaskRelation> processTaskRelations =
                    processService.findRelationByCode(processInstance.getProcessDefinitionCode(),
                            processInstance.getProcessDefinitionVersion());
            List<TaskDefinitionLog> taskDefinitionLogs =
                    taskDefinitionLogDao.queryTaskDefineLogList(processTaskRelations);
            Map<Long, String> taskDefinitionCodeMap =
                    taskDefinitionLogs.stream().filter(taskDefinitionLog -> taskDefinitionLog.getFlag() == Flag.YES)
                            .collect(Collectors.toMap(TaskDefinitionLog::getCode, TaskDefinitionLog::getName));

            List<TaskInstance> taskInstanceList =
                    taskInstanceDao.queryLastTaskInstanceListIntervalInProcessInstance(processInstance.getId(),
                            taskDefinitionCodeMap.keySet(), testFlag);
            Map<Long, TaskExecutionStatus> taskExecutionStatusMap =
                    taskInstanceList.stream()
                            .filter(taskInstance -> taskInstance.getTaskExecuteType() != TaskExecuteType.STREAM)
                            .collect(Collectors.toMap(TaskInstance::getTaskCode, TaskInstance::getState));

            for (Long taskCode : taskDefinitionCodeMap.keySet()) {
                if (!taskExecutionStatusMap.containsKey(taskCode)) {
                    log.warn(
                            "The task of the workflow is not being executed, taskCode: {}, processInstanceId: {}, processName: {}.",
                            taskCode, processInstance.getProcessDefinitionCode(), processInstance.getName());
                    return DependResult.FAILED;
                } else {
                    if (!taskExecutionStatusMap.get(taskCode).isSuccess()) {
                        log.warn(
                                "The task of the workflow is not being executed successfully, taskCode: {}, processInstanceId: {}, processName: {}.",
                                taskCode, processInstance.getProcessDefinitionCode(), processInstance.getName());
                        return DependResult.FAILED;
                    }
                }
            }
            addItemVarPool(processInstance.getVarPool(), processInstance.getEndTime().getTime());
            return DependResult.SUCCESS;
        }
        return DependResult.FAILED;
    }

    /**
     * depend type = depend_task
     *
     * @param processInstance last process instance in the date interval
     * @param depTaskCode the dependent task code
     * @param testFlag test flag
     * @return depend result
     */
    private DependResult dependResultBySingleTaskInstance(ProcessInstance processInstance, long depTaskCode,
                                                          int testFlag) {
        TaskInstance taskInstance =
                taskInstanceDao.queryLastTaskInstanceIntervalInProcessInstance(processInstance.getId(),
                        depTaskCode, testFlag);

        if (taskInstance == null) {
            TaskDefinition taskDefinition = taskDefinitionDao.queryByCode(depTaskCode);

            if (taskDefinition == null) {
                log.error("The dependent task definition can not be find, so return depend failed, taskCode: {}",
                        depTaskCode);
                return DependResult.FAILED;
            }

            if (taskDefinition.getFlag() == Flag.NO) {
                log.info(
                        "The dependent task is a forbidden task, so return depend success. Task code: {}, task name: {}",
                        taskDefinition.getCode(), taskDefinition.getName());
                return DependResult.SUCCESS;
            }

            if (!processInstance.getState().isFinished()) {
                log.info("Wait for the dependent workflow to complete, processCode: {}, processInstanceId: {}.",
                        processInstance.getProcessDefinitionCode(), processInstance.getId());
                return DependResult.WAITING;
            }

            return DependResult.FAILED;
        } else {
            if (TaskExecuteType.STREAM == taskInstance.getTaskExecuteType()) {
                log.info(
                        "The dependent task is a streaming task, so return depend success. Task code: {}, task name: {}.",
                        taskInstance.getTaskCode(), taskInstance.getName());
                addItemVarPool(taskInstance.getVarPool(), taskInstance.getEndTime().getTime());
                return DependResult.SUCCESS;
            }
            return getDependResultOfTask(processInstance, taskInstance);
        }
    }

    /**
     * add varPool to dependItemVarPoolMap
     *
     * @param varPoolStr
     * @param endTime
     */
    private void addItemVarPool(String varPoolStr, Long endTime) {
        List<Property> varPool = new ArrayList<>(JSONUtils.toList(varPoolStr, Property.class));
        if (!varPool.isEmpty()) {
            Map<String, Property> varPoolPropertyMap = varPool.stream().filter(p -> p.getDirect().equals(Direct.OUT))
                    .collect(Collectors.toMap(Property::getProp, Function.identity()));
            Map<String, Long> varPoolEndTimeMap = varPool.stream().filter(p -> p.getDirect().equals(Direct.OUT))
                    .collect(Collectors.toMap(Property::getProp, d -> endTime));
            dependItemVarPoolPropertyMap.putAll(varPoolPropertyMap);
            dependItemVarPoolEndTimeMap.putAll(varPoolEndTimeMap);
        }
    }

    /**
     * find the last one process instance that :
     * 1. manual run and finish between the interval
     * 2. schedule run and schedule time between the interval
     *
     * @param definitionCode definition code
     * @param taskCode task code
     * @param dateInterval   date interval
     * @return ProcessInstance
     */
    private ProcessInstance findLastProcessInterval(Long definitionCode, Long taskCode, DateInterval dateInterval,
                                                    int testFlag) {

        ProcessInstance lastSchedulerProcess =
                processInstanceDao.queryLastSchedulerProcessInterval(definitionCode, taskCode, dateInterval, testFlag);

        ProcessInstance lastManualProcess =
                processInstanceDao.queryLastManualProcessInterval(definitionCode, taskCode, dateInterval, testFlag);

        if (lastManualProcess == null) {
            return lastSchedulerProcess;
        }
        if (lastSchedulerProcess == null) {
            return lastManualProcess;
        }

        // In the time range, there are both manual and scheduled workflow instances, return the last workflow instance
        return lastManualProcess.getId() > lastSchedulerProcess.getId() ? lastManualProcess : lastSchedulerProcess;
    }

    /**
     * get dependent result by task/process instance
     *
     * @param processInstance process instance
     * @param taskInstance task instance
     * @return DependResult
     */
    private DependResult getDependResultOfTask(ProcessInstance processInstance, TaskInstance taskInstance) {

        TaskExecutionStatus state = taskInstance.getState();
        if (!state.isFinished()) {
            return DependResult.WAITING;
        } else if (state.isSuccess()) {
            return DependResult.SUCCESS;
        } else {
            if (processInstance.getState().isRunning()
                    && taskInstance.getRetryTimes() < taskInstance.getMaxRetryTimes()) {
                log.info("taskDefinitionCode: {}, taskDefinitionName: {}, retryTimes: {}, maxRetryTimes: {}",
                        taskInstance.getTaskCode(), taskInstance.getName(), taskInstance.getRetryTimes(),
                        taskInstance.getMaxRetryTimes());
                return DependResult.WAITING;
            }
            log.warn(
                    "The dependent task were not executed successfully, so return depend failed. Task code: {}, task name: {}.",
                    taskInstance.getTaskCode(), taskInstance.getName());
            return DependResult.FAILED;
        }
    }

    /**
     * judge depend item finished
     *
     * @param currentTime current time
     * @return boolean
     */
    public boolean finish(Date currentTime, int testFlag, DependentParameters.DependentFailurePolicyEnum failurePolicy,
                          Integer failureWaitingTime) {
        DependResult modelDependResult = getModelDependResult(currentTime, testFlag);
        if (modelDependResult == DependResult.WAITING) {
            return false;
        } else if (modelDependResult == DependResult.FAILED && DEPENDENT_FAILURE_WAITING == failurePolicy
                && failureWaitingTime != null) {
            return Duration.between(currentTime.toInstant(), Instant.now())
                    .compareTo(Duration.ofMinutes(failureWaitingTime)) > 0;
        }
        return true;
    }

    /**
     * get model depend result
     *
     * @param currentTime current time
     * @return DependResult
     */
    public DependResult getModelDependResult(Date currentTime, int testFlag) {

        List<DependResult> dependResultList = new ArrayList<>();

        for (DependentItem dependentItem : dependItemList) {
            if (isSelfDependent(dependentItem) && isFirstProcessInstance(dependentItem)) {
                // if self-dependent, default success at first time
                dependResultMap.put(dependentItem.getKey(), DependResult.SUCCESS);
                dependResultList.add(DependResult.SUCCESS);
                log.info(
                        "This dependent item is self-dependent and run at first time, default success, processDefinitionCode:{}, depTaskCode:{}",
                        dependentItem.getDefinitionCode(), dependentItem.getDepTaskCode());
                continue;
            }
            DependResult dependResult = getDependResultForItem(dependentItem, currentTime, testFlag);
            if (dependResult != DependResult.WAITING && dependResult != DependResult.FAILED) {
                dependResultMap.put(dependentItem.getKey(), dependResult);
                if (dependentItem.getParameterPassing() && !dependItemVarPoolPropertyMap.isEmpty()) {
                    DependentUtils.addTaskVarPool(dependItemVarPoolPropertyMap, dependItemVarPoolEndTimeMap,
                            dependTaskVarPoolPropertyMap, dependTaskVarPoolEndTimeMap);
                }
            }
            dependItemVarPoolPropertyMap.clear();
            dependItemVarPoolEndTimeMap.clear();
            dependResultList.add(dependResult);
        }
        return DependentUtils.getDependResultForRelation(this.relation, dependResultList);
    }

    /**
     * get dependent item result
     *
     * @param item        item
     * @param currentTime current time
     * @return DependResult
     */
    private DependResult getDependResultForItem(DependentItem item, Date currentTime, int testFlag) {
        String key = item.getKey();
        if (dependResultMap.containsKey(key)) {
            return dependResultMap.get(key);
        }
        return getDependentResultForItem(item, currentTime, testFlag);
    }

    public Map<String, DependResult> getDependResultMap() {
        return dependResultMap;
    }

    public Map<String, Property> getDependTaskVarPoolPropertyMap() {
        return dependTaskVarPoolPropertyMap;
    }

    public Map<String, Long> getDependTaskVarPoolEndTimeMap() {
        return dependTaskVarPoolEndTimeMap;
    }

    /**
     * check for self-dependent
     * @param dependentItem
     * @return
     */
    public boolean isSelfDependent(DependentItem dependentItem) {
        if (processInstance.getProcessDefinitionCode().equals(dependentItem.getDefinitionCode())) {
            if (dependentItem.getDepTaskCode() == Constants.DEPENDENT_ALL_TASK_CODE) {
                return true;
            }
            if (dependentItem.getDepTaskCode() == taskInstance.getTaskCode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * check for first-running
     * query the first processInstance by scheduleTime(or startTime if scheduleTime is null)
     * @param dependentItem
     * @return
     */
    public boolean isFirstProcessInstance(DependentItem dependentItem) {
        ProcessInstance firstProcessInstance =
                processInstanceDao.queryFirstScheduleProcessInstance(dependentItem.getDefinitionCode());
        if (firstProcessInstance == null) {
            firstProcessInstance = processInstanceDao.queryFirstStartProcessInstance(dependentItem.getDefinitionCode());
            if (firstProcessInstance == null) {
                log.warn("First process instance is null, processDefinitionCode:{}", dependentItem.getDefinitionCode());
                return false;
            }
        }
        if (firstProcessInstance.getId() == processInstance.getId()) {
            return true;
        }
        return false;
    }
}
