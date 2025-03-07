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

package org.apache.dolphinscheduler.server.master.runner.dispatcher;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.dolphinscheduler.extract.base.client.SingletonJdkDynamicRpcClientProxyFactory;
import org.apache.dolphinscheduler.extract.base.utils.Host;
import org.apache.dolphinscheduler.extract.worker.ITaskInstanceOperator;
import org.apache.dolphinscheduler.extract.worker.transportor.TaskInstanceDispatchRequest;
import org.apache.dolphinscheduler.extract.worker.transportor.TaskInstanceDispatchResponse;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.server.master.cluster.loadbalancer.IWorkerLoadBalancer;
import org.apache.dolphinscheduler.server.master.engine.task.runnable.ITaskExecutionRunnable;
import org.apache.dolphinscheduler.server.master.exception.dispatch.TaskDispatchException;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkerTaskDispatcher extends BaseTaskDispatcher {

    private final IWorkerLoadBalancer workerLoadBalancer;

    public WorkerTaskDispatcher(IWorkerLoadBalancer workerLoadBalancer) {
        this.workerLoadBalancer = checkNotNull(workerLoadBalancer);
    }

    @Override
    protected void doDispatch(ITaskExecutionRunnable ITaskExecutionRunnable) throws TaskDispatchException {
        final TaskExecutionContext taskExecutionContext = ITaskExecutionRunnable.getTaskExecutionContext();
        final String taskName = taskExecutionContext.getTaskName();
        final String workerAddress = taskExecutionContext.getHost();
        try {
            final TaskInstanceDispatchResponse taskInstanceDispatchResponse = SingletonJdkDynamicRpcClientProxyFactory
                    .withService(ITaskInstanceOperator.class)
                    .withHost(workerAddress)
                    .dispatchTask(new TaskInstanceDispatchRequest(ITaskExecutionRunnable.getTaskExecutionContext()));
            if (!taskInstanceDispatchResponse.isDispatchSuccess()) {
                throw new TaskDispatchException("Dispatch task: " + taskName + " to " + workerAddress + " failed: "
                        + taskInstanceDispatchResponse);
            }
        } catch (TaskDispatchException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskDispatchException("Dispatch task: " + taskName + " to " + workerAddress + " failed", e);
        }
    }

    @Override
    protected Optional<Host> getTaskInstanceDispatchHost(ITaskExecutionRunnable ITaskExecutionRunnable) {
        String workerGroup = ITaskExecutionRunnable.getTaskExecutionContext().getWorkerGroup();
        return workerLoadBalancer.select(workerGroup).map(Host::of);
    }
}
