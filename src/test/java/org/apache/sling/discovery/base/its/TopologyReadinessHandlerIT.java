/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.discovery.base.its;

import static org.junit.Assert.*;

import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.sling.discovery.base.its.setup.VirtualInstance;
import org.apache.sling.discovery.base.its.setup.VirtualInstanceBuilder;
import org.apache.sling.discovery.base.connectors.DummyVirtualInstanceBuilder;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class TopologyReadinessHandlerIT {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SystemReady systemReadyService = new SystemReady() {};
    private final List<VirtualInstance> instances = new LinkedList<>();

    @After
    public void tearDown() throws Exception {
        for (VirtualInstance instance : instances) {
            instance.stop();
        }
        instances.clear();
    }

    @Test
    public void testReadinessHandlerStateTransitions() throws Exception {
        logger.info("testReadinessHandlerStateTransitions: start");

        // Create two instances
        VirtualInstance instance1 = createInstance("instance1");
        VirtualInstance instance2 = createInstance("instance2");

        // Initially, both instances should delay topology changes (STARTUP state)
        assertTrue("Instance1 should delay topology changes initially",
                instance1.getReadinessHandler().shouldTriggerTopologyChanging());
        assertTrue("Instance2 should delay topology changes initially",
                instance2.getReadinessHandler().shouldTriggerTopologyChanging());

        // Bind SystemReady to instance1 - should transition to READY state
        instance1.getReadinessHandler().bindSystemReady(systemReadyService);
        assertFalse("Instance1 should not delay topology changes after SystemReady",
                instance1.getReadinessHandler().shouldTriggerTopologyChanging());
        assertTrue("Instance2 should still delay topology changes",
                instance2.getReadinessHandler().shouldTriggerTopologyChanging());

        // Bind SystemReady to instance2 - should transition to READY state
        instance2.getReadinessHandler().bindSystemReady(systemReadyService);
        assertFalse("Instance2 should not delay topology changes after SystemReady",
                instance2.getReadinessHandler().shouldTriggerTopologyChanging());

        // Now we can safely stop instance2 since it's in READY state
        logger.info("Stopping instance2");
        instance2.stop();
        instances.remove(instance2); // Remove from cleanup list

        // Verify instance1 remains in READY state
        assertFalse("Instance1 should remain in READY state after instance2 failure",
                instance1.getReadinessHandler().shouldTriggerTopologyChanging());

        logger.info("testReadinessHandlerStateTransitions: end");
    }

    private VirtualInstance createInstance(String debugName) throws Exception {
        VirtualInstanceBuilder builder = new DummyVirtualInstanceBuilder()
                .setDebugName(debugName)
                .newRepository("/var/discovery/impl/" + debugName, true)
                .setConnectorPingTimeout(3)
                .setMinEventDelay(1);
        VirtualInstance instance = builder.build();
        instances.add(instance);
        instance.startViewChecker(1);
        return instance;
    }
}
