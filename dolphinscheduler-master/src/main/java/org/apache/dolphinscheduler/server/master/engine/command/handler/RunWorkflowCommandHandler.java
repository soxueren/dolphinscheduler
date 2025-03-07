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

package org.apache.dolphinscheduler.server.master.engine.command.handler;

import org.apache.dolphinscheduler.common.enums.CommandType;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.WarningType;
import org.apache.dolphinscheduler.common.enums.WorkflowExecutionStatus;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.Command;
import org.apache.dolphinscheduler.dao.entity.ProcessDefinition;
import org.apache.dolphinscheduler.dao.entity.ProcessInstance;
import org.apache.dolphinscheduler.dao.repository.ProcessInstanceDao;
import org.apache.dolphinscheduler.dao.repository.TaskInstanceDao;
import org.apache.dolphinscheduler.dao.utils.EnvironmentUtils;
import org.apache.dolphinscheduler.dao.utils.WorkerGroupUtils;
import org.apache.dolphinscheduler.extract.master.command.ICommandParam;
import org.apache.dolphinscheduler.extract.master.command.RunWorkflowCommandParam;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;
import org.apache.dolphinscheduler.server.master.engine.graph.IWorkflowGraph;
import org.apache.dolphinscheduler.server.master.engine.graph.WorkflowExecutionGraph;
import org.apache.dolphinscheduler.server.master.engine.graph.WorkflowGraphBfsVisitor;
import org.apache.dolphinscheduler.server.master.engine.task.runnable.TaskExecutionRunnable;
import org.apache.dolphinscheduler.server.master.engine.task.runnable.TaskExecutionRunnableBuilder;
import org.apache.dolphinscheduler.server.master.runner.WorkflowExecuteContext.WorkflowExecuteContextBuilder;
import org.apache.dolphinscheduler.service.expand.CuringParamsService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Used to handle the {@link CommandType#START_PROCESS} which will start the workflow definition.
 * <p> You can specify the start nodes at {@link RunWorkflowCommandParam}
 */
@Component
public class RunWorkflowCommandHandler extends AbstractCommandHandler {

    @Autowired
    private ProcessInstanceDao processInstanceDao;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private MasterConfig masterConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CuringParamsService curingParamsService;

    /**
     * Will generate a new workflow instance based on the command.
     */
    @Override
    protected void assembleWorkflowInstance(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final ProcessDefinition workflowDefinition = workflowExecuteContextBuilder.getWorkflowDefinition();
        final Command command = workflowExecuteContextBuilder.getCommand();

        final ProcessInstance processInstance = new ProcessInstance();
        processInstance.setProcessDefinitionCode(workflowDefinition.getCode());
        processInstance.setProcessDefinitionVersion(workflowDefinition.getVersion());
        processInstance.setProjectCode(workflowDefinition.getProjectCode());
        processInstance.setStateWithDesc(WorkflowExecutionStatus.RUNNING_EXECUTION, command.getCommandType().name());
        processInstance.setRecovery(Flag.NO);
        processInstance.setStartTime(new Date());
        processInstance.setRestartTime(processInstance.getStartTime());
        processInstance.setEndTime(null);
        processInstance.setRunTimes(1);
        processInstance.setName(String.join("-", workflowDefinition.getName(), DateUtils.getCurrentTimeStamp()));
        processInstance.setHost(masterConfig.getMasterAddress());
        processInstance.setCommandType(command.getCommandType());
        processInstance.setCommandParam(command.getCommandParam());
        processInstance.setTaskDependType(command.getTaskDependType());
        processInstance.setFailureStrategy(command.getFailureStrategy());
        processInstance.setWarningType(ObjectUtils.defaultIfNull(command.getWarningType(), WarningType.NONE));
        processInstance.setWarningGroupId(command.getWarningGroupId());
        processInstance.setScheduleTime(command.getScheduleTime());
        // todo: merge the global params or add startup params
        // or can we merge this after

        processInstance.setGlobalParams(mergeCommandParamsWithWorkflowParams(command, workflowDefinition));
        processInstance.setExecutorId(command.getExecutorId());
        processInstance.setExecutorName(null);
        processInstance.setTenantCode(command.getTenantCode());
        processInstance.setIsSubProcess(Flag.NO);
        processInstance.addHistoryCmd(command.getCommandType());
        processInstance.setProcessInstancePriority(command.getProcessInstancePriority());
        processInstance.setWorkerGroup(WorkerGroupUtils.getWorkerGroupOrDefault(command.getWorkerGroup()));
        processInstance.setEnvironmentCode(EnvironmentUtils.getEnvironmentCodeOrDefault(command.getEnvironmentCode()));
        processInstance.setTimeout(workflowDefinition.getTimeout());
        processInstance.setVarPool(null);
        processInstance.setDryRun(command.getDryRun());
        processInstance.setTestFlag(command.getTestFlag());
        processInstanceDao.insert(processInstance);

        workflowExecuteContextBuilder.setWorkflowInstance(processInstance);
    }

    @Override
    protected void assembleWorkflowExecutionGraph(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final IWorkflowGraph workflowGraph = workflowExecuteContextBuilder.getWorkflowGraph();
        final WorkflowExecutionGraph workflowExecutionGraph = new WorkflowExecutionGraph();
        final BiConsumer<String, Set<String>> taskExecutionRunnableCreator = (task, successors) -> {
            final TaskExecutionRunnableBuilder taskExecutionRunnableBuilder =
                    TaskExecutionRunnableBuilder
                            .builder()
                            .workflowExecutionGraph(workflowExecutionGraph)
                            .workflowDefinition(workflowExecuteContextBuilder.getWorkflowDefinition())
                            .workflowInstance(workflowExecuteContextBuilder.getWorkflowInstance())
                            .taskDefinition(workflowGraph.getTaskNodeByName(task))
                            .workflowEventBus(workflowExecuteContextBuilder.getWorkflowEventBus())
                            .applicationContext(applicationContext)
                            .build();
            workflowExecutionGraph.addNode(new TaskExecutionRunnable(taskExecutionRunnableBuilder));
            workflowExecutionGraph.addEdge(task, successors);
        };

        final WorkflowGraphBfsVisitor workflowGraphBfsVisitor = WorkflowGraphBfsVisitor.builder()
                .taskDependType(workflowExecuteContextBuilder.getWorkflowInstance().getTaskDependType())
                .onWorkflowGraph(workflowGraph)
                .fromTask(parseStartNodesFromWorkflowInstance(workflowExecuteContextBuilder))
                .doVisitFunction(taskExecutionRunnableCreator)
                .build();
        workflowGraphBfsVisitor.visit();

        workflowExecuteContextBuilder.setWorkflowExecutionGraph(workflowExecutionGraph);
    }

    /**
     * Merge the command params with the workflow params.
     * <p> If there are duplicate keys, the command params will override the workflow params.
     */
    private String mergeCommandParamsWithWorkflowParams(final Command command,
                                                        final ProcessDefinition workflowDefinition) {
        final List<Property> commandParams =
                Optional.ofNullable(JSONUtils.parseObject(command.getCommandParam(), ICommandParam.class))
                        .map(ICommandParam::getCommandParams)
                        .orElse(null);
        final List<Property> globalParamsList = JSONUtils.toList(workflowDefinition.getGlobalParams(), Property.class);
        Map<String, Property> finalParams = new HashMap<>();
        if (CollectionUtils.isNotEmpty(globalParamsList)) {
            globalParamsList.forEach(globalParam -> finalParams.put(globalParam.getProp(), globalParam));
        }
        if (CollectionUtils.isNotEmpty(commandParams)) {
            commandParams.forEach(commandParam -> finalParams.put(commandParam.getProp(), commandParam));
        }
        return JSONUtils.toJsonString(finalParams.values());
    }

    @Override
    public CommandType commandType() {
        return CommandType.START_PROCESS;
    }
}
