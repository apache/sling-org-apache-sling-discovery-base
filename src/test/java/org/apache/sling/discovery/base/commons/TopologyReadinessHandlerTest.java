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
package org.apache.sling.discovery.base.commons;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.sling.discovery.TopologyView;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

public class TopologyReadinessHandlerTest {

    private TopologyReadinessHandler handler;
    private SystemReady systemReadyService;
    private ComponentContext componentContext;
    private TopologyView oldView;

    @Before
    public void setup() {
        handler = new TopologyReadinessHandler();
        systemReadyService = mock(SystemReady.class);
        componentContext = mock(ComponentContext.class);
        BundleContext bundleContext = mock(BundleContext.class);
        ServiceReference<SystemReady> serviceRef = mock(ServiceReference.class);
        
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getServiceReference(SystemReady.class)).thenReturn(serviceRef);
        when(bundleContext.getService(serviceRef)).thenReturn(systemReadyService);

        oldView = mock(TopologyView.class);
        handler.activate(componentContext);
    }

    @Test
    public void testSystemReady() {
        // Simulate the system transitioning to the READY state
        handler.bindSystemReady(systemReadyService);
        assertFalse("Expected no delay when the system is ready", handler.shouldTriggerTopologyChanging());
    }

    @Test
    public void testFailingSystemReadyService() {
        // Transition to READY state by binding the SystemReady service
        handler.bindSystemReady(systemReadyService);
        handler.deactivate(componentContext);
        assertTrue("Expected delay when no SystemReady Service is available", handler.shouldTriggerTopologyChanging());
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidTransitionFromShutdown() {
        handler.bindSystemReady(systemReadyService);
        handler.deactivate(null);
        handler.bindSystemReady(systemReadyService);
    }

    @Test
    public void testValidTransitionSequence() {
        // Initially, the system should be in the STARTUP state
        assertTrue(handler.shouldTriggerTopologyChanging()); // STARTUP state delays changes

        // Transition to READY state by binding the SystemReady service
        handler.bindSystemReady(systemReadyService);
        assertFalse(handler.shouldTriggerTopologyChanging()); // READY state does not delay changes

        // Transition to SHUTDOWN state by unbind SystemReady (transitions to SHUTDOWN)
        handler.unbindSystemReady(systemReadyService);
        assertTrue(handler.shouldTriggerTopologyChanging()); // SHUTDOWN state delays changes
    }

    @Test
    public void testShutdownSequence() {
        // Setup initial READY state
        handler.activate(componentContext);
        handler.bindSystemReady(systemReadyService);

        assertFalse("Should not delay topology changes in READY state",
                handler.shouldTriggerTopologyChanging());

        // Simulate shutdown
        handler.deactivate(null);

        // After shutdown, should delay topology changes
        assertTrue("Should delay topology changes after shutdown",
                handler.shouldTriggerTopologyChanging());
    }
} 
