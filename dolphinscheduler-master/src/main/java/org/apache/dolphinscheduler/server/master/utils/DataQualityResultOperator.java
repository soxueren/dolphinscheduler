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

import org.apache.dolphinscheduler.service.alert.ProcessAlertManager;
import org.apache.dolphinscheduler.service.process.ProcessService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DataQualityResultOperator
 */
@Component
@Slf4j
public class DataQualityResultOperator {

    @Autowired
    private ProcessService processService;

    @Autowired
    private ProcessAlertManager alertManager;

    // /**
    // * When the task type is data quality, it will get the statistics value、comparison value、
    // * threshold、check type、operator and failure strategy，use the formula that
    // * {check type} {operator} {threshold} to get dqc result . If result is failure, it will alert or block
    // * @param taskResponseEvent
    // * @param taskInstance
    // */
    // public void operateDqExecuteResult(TaskEvent taskResponseEvent, TaskInstance taskInstance) {
    // if (TASK_TYPE_DATA_QUALITY.equals(taskInstance.getTaskType())) {
    //
    // ProcessInstance processInstance =
    // processService.findProcessInstanceDetailById(taskInstance.getProcessInstanceId()).orElse(null);
    //
    // // when the task is failure or cancel, will delete the execute result and statistics value
    // if (taskResponseEvent.getState().isFailure()
    // || taskResponseEvent.getState().isKill()) {
    // processService.deleteDqExecuteResultByTaskInstanceId(taskInstance.getId());
    // processService.deleteTaskStatisticsValueByTaskInstanceId(taskInstance.getId());
    // sendDqTaskErrorAlert(taskInstance, processInstance);
    // return;
    // }
    //
    // processService.updateDqExecuteResultUserId(taskInstance.getId());
    // DqExecuteResult dqExecuteResult =
    // processService.getDqExecuteResultByTaskInstanceId(taskInstance.getId());
    // if (dqExecuteResult != null) {
    // // check the result ,if result is failure do some operator by failure strategy
    // checkDqExecuteResult(taskResponseEvent, dqExecuteResult, processInstance);
    // }
    // }
    // }
    //
    // /**
    // * get the data quality check result
    // * and if the result is failure that will alert or block
    // * @param taskResponseEvent
    // * @param dqExecuteResult
    // * @param processInstance
    // */
    // private void checkDqExecuteResult(TaskEvent taskResponseEvent,
    // DqExecuteResult dqExecuteResult,
    // ProcessInstance processInstance) {
    // if (isFailed(dqExecuteResult)) {
    // DqFailureStrategy dqFailureStrategy = DqFailureStrategy.of(dqExecuteResult.getFailureStrategy());
    // if (dqFailureStrategy != null) {
    // dqExecuteResult.setState(DqTaskState.FAILURE.getCode());
    // sendDqTaskResultAlert(dqExecuteResult, processInstance);
    // switch (dqFailureStrategy) {
    // case ALERT:
    // log.info("task is failure, continue and alert");
    // break;
    // case BLOCK:
    // taskResponseEvent.setState(TaskExecutionStatus.FAILURE);
    // log.info("task is failure, end and alert");
    // break;
    // default:
    // break;
    // }
    // }
    // } else {
    // dqExecuteResult.setState(DqTaskState.SUCCESS.getCode());
    // }
    //
    // processService.updateDqExecuteResultState(dqExecuteResult);
    // }
    //
    // /**
    // * It is used to judge whether the result of the data quality task is failed
    // * @param dqExecuteResult
    // * @return
    // */
    // private boolean isFailed(DqExecuteResult dqExecuteResult) {
    // CheckType checkType = CheckType.of(dqExecuteResult.getCheckType());
    //
    // double statisticsValue = dqExecuteResult.getStatisticsValue();
    // double comparisonValue = dqExecuteResult.getComparisonValue();
    // double threshold = dqExecuteResult.getThreshold();
    //
    // OperatorType operatorType = OperatorType.of(dqExecuteResult.getOperator());
    //
    // boolean isFailed = false;
    // if (operatorType != null) {
    // double srcValue = 0;
    // switch (checkType) {
    // case COMPARISON_MINUS_STATISTICS:
    // srcValue = comparisonValue - statisticsValue;
    // isFailed = !getCompareResult(operatorType, srcValue, threshold);
    // break;
    // case STATISTICS_MINUS_COMPARISON:
    // srcValue = statisticsValue - comparisonValue;
    // isFailed = !getCompareResult(operatorType, srcValue, threshold);
    // break;
    // case STATISTICS_COMPARISON_PERCENTAGE:
    // if (comparisonValue > 0) {
    // srcValue = statisticsValue / comparisonValue * 100;
    // }
    // isFailed = !getCompareResult(operatorType, srcValue, threshold);
    // break;
    // case STATISTICS_COMPARISON_DIFFERENCE_COMPARISON_PERCENTAGE:
    // if (comparisonValue > 0) {
    // srcValue = Math.abs(comparisonValue - statisticsValue) / comparisonValue * 100;
    // }
    // isFailed = !getCompareResult(operatorType, srcValue, threshold);
    // break;
    // default:
    // break;
    // }
    // }
    //
    // return isFailed;
    // }
    //
    // private void sendDqTaskResultAlert(DqExecuteResult dqExecuteResult, ProcessInstance processInstance) {
    // alertManager.sendDataQualityTaskExecuteResultAlert(dqExecuteResult, processInstance);
    // }
    //
    // private void sendDqTaskErrorAlert(TaskInstance taskInstance, ProcessInstance processInstance) {
    // alertManager.sendTaskErrorAlert(taskInstance, processInstance);
    // }
    //
    // private boolean getCompareResult(OperatorType operatorType, double srcValue, double targetValue) {
    // BigDecimal src = BigDecimal.valueOf(srcValue);
    // BigDecimal target = BigDecimal.valueOf(targetValue);
    // switch (operatorType) {
    // case EQ:
    // return src.compareTo(target) == 0;
    // case LT:
    // return src.compareTo(target) <= -1;
    // case LE:
    // return src.compareTo(target) == 0 || src.compareTo(target) <= -1;
    // case GT:
    // return src.compareTo(target) >= 1;
    // case GE:
    // return src.compareTo(target) == 0 || src.compareTo(target) >= 1;
    // case NE:
    // return src.compareTo(target) != 0;
    // default:
    // return true;
    // }
    // }
}
