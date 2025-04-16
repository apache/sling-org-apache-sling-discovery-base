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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEvent.Type;
import org.apache.sling.discovery.TopologyView;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

public class TopologyDelayHandlerTest {

    private TopologyDelayHandler handler;
    private SystemReadyService systemReadyService;
    private ComponentContext componentContext;
    private TopologyView oldView;

    @Before
    public void setup() {
        handler = new TopologyDelayHandler();
        systemReadyService = mock(SystemReadyService.class);
        componentContext = mock(ComponentContext.class);
        BundleContext bundleContext = mock(BundleContext.class);
        ServiceReference<SystemReadyService> serviceRef = mock(ServiceReference.class);
        
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getServiceReference(SystemReadyService.class)).thenReturn(serviceRef);
        when(bundleContext.getService(serviceRef)).thenReturn(systemReadyService);

        oldView = mock(TopologyView.class);
        handler.activate(componentContext);
        handler.setDelayDuration(1000); // 1 second delay for testing
    }

    @Test
    public void testSystemNotReady() {
        when(systemReadyService.isSystemReady()).thenReturn(false);
        handler.setSystemReady(false);

        TopologyEvent event = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertTrue(handler.shouldDelayTopologyChange(event));
    }

    @Test
    public void testSystemReady() {
        when(systemReadyService.isSystemReady()).thenReturn(true);
        handler.setSystemReady(true);

        TopologyEvent event = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertFalse(handler.shouldDelayTopologyChange(event));
    }

    @Test
    public void testTopologyChangeInProgress() {
        when(systemReadyService.isSystemReady()).thenReturn(true);
        handler.setSystemReady(true);
        handler.startTopologyChange();

        TopologyEvent event = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertTrue(handler.shouldDelayTopologyChange(event));
    }

    @Test
    public void testDelayPeriod() throws InterruptedException {
        when(systemReadyService.isSystemReady()).thenReturn(true);
        handler.setSystemReady(true);
        handler.startTopologyChange();
        handler.endTopologyChange();

        // Should still be in delay period
        TopologyEvent event = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertTrue(handler.shouldDelayTopologyChange(event));

        // Wait for delay period to expire
        Thread.sleep(1100);

        // Should no longer be in delay period
        assertFalse(handler.shouldDelayTopologyChange(event));
    }

    @Test
    public void testNullEvent() {
        assertFalse(handler.shouldDelayTopologyChange(null));
    }

    @Test
    public void testNoSystemReadyService() {
        handler.deactivate(componentContext);
        handler.setSystemReady(false);

        TopologyEvent event = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertFalse(handler.shouldDelayTopologyChange(event));
    }

    @Test
    public void testTopologyEventTypes() {
        when(systemReadyService.isSystemReady()).thenReturn(true);
        handler.setSystemReady(true);
        TopologyView newView = mock(TopologyView.class);

        // TOPOLOGY_INIT should not be delayed
        TopologyEvent initEvent = new TopologyEvent(Type.TOPOLOGY_INIT, null, newView);
        assertFalse(handler.shouldDelayTopologyChange(initEvent));

        // TOPOLOGY_CHANGING should be delayed if in progress
        handler.startTopologyChange();
        TopologyEvent changingEvent = new TopologyEvent(Type.TOPOLOGY_CHANGING, oldView, null);
        assertTrue(handler.shouldDelayTopologyChange(changingEvent));

        // TOPOLOGY_CHANGED should not be delayed
        handler.endTopologyChange();
        TopologyEvent changedEvent = new TopologyEvent(Type.TOPOLOGY_CHANGED, oldView, newView);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertFalse(handler.shouldDelayTopologyChange(changedEvent));
    }
} 
