/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.flowext;

import org.onosproject.net.flow.FlowRuleBatchRequest;
import org.onosproject.net.flow.FlowRuleService;

import java.util.concurrent.Future;

/**
 * Experimental extension to the flow rule subsystem; still under development.
 * Service for injecting extended flow rules into the environment.
 * This service just send the packet downstream. It won't store the
 * flowRuleExtension in cache.
 */
public interface FlowRuleExtService extends FlowRuleService {
    /**
     * Applies a batch operation of FlowRules.
     * this batch can be divided into many sub-batch by deviceId, and application
     * gives a batchId, it means once one flowRule apply failed, all flow rules should
     * withdraw.
     *
     * @param batch batch operation to apply
     * @return future indicating the state of the batch operation
     */
    Future<FlowExtCompletedOperation> applyBatch(FlowRuleBatchRequest batch);
}
