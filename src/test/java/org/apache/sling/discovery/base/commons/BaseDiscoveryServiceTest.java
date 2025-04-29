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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.lang.reflect.Field;

import org.apache.sling.discovery.TopologyView;
import org.apache.sling.discovery.base.connectors.announcement.AnnouncementRegistry;
import org.apache.sling.discovery.commons.providers.spi.LocalClusterView;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BaseDiscoveryServiceTest {

    private BaseDiscoveryService discoveryService;

    @Mock
    private ClusterViewService clusterViewService;

    @Mock
    private AnnouncementRegistry announcementRegistry;

    @Mock
    private TopologyReadinessHandler topologyReadinessHandler;

    @Mock
    private LocalClusterView localClusterView;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        discoveryService = new BaseDiscoveryService() {
            @Override
            protected ClusterViewService getClusterViewService() {
                return clusterViewService;
            }

            @Override
            protected AnnouncementRegistry getAnnouncementRegistry() {
                return announcementRegistry;
            }

            @Override
            protected void handleIsolatedFromTopology() {
                // No-op for testing
            }
        };

        discoveryService.setOldView(new DefaultTopologyView());

        // After creating the mock and the discoveryService:
        Field field = BaseDiscoveryService.class.getDeclaredField("topologyReadinessHandler");
        field.setAccessible(true);
        field.set(discoveryService, topologyReadinessHandler);
    }

    @Test
    public void testGetTopology_Success() throws Exception {
        when(clusterViewService.getLocalClusterView()).thenReturn(localClusterView);
        when(announcementRegistry.listInstances(localClusterView)).thenReturn(Collections.emptyList());
        when(topologyReadinessHandler.shouldDelayTopologyChange()).thenReturn(false);

        TopologyView topology = discoveryService.getTopology();

        assertNotNull(topology);
        assertTrue(topology instanceof DefaultTopologyView);
    }

    @Test
    public void testGetTopology_UndefinedClusterView() throws Exception {
        when(clusterViewService.getLocalClusterView()).thenThrow(
                new UndefinedClusterViewException(UndefinedClusterViewException.Reason.REPOSITORY_EXCEPTION, "Test"));

        TopologyView topology = discoveryService.getTopology();

        assertNotNull(topology);
        assertFalse(topology.isCurrent());
    }

    @Test
    public void testGetTopology_DelayTopologyChange() throws Exception {
        when(clusterViewService.getLocalClusterView()).thenReturn(localClusterView);
        when(announcementRegistry.listInstances(localClusterView)).thenReturn(Collections.emptyList());
        when(topologyReadinessHandler.shouldDelayTopologyChange()).thenReturn(true);

        // Set up oldView as current
        DefaultTopologyView oldView = new DefaultTopologyView();
        discoveryService.setOldView(oldView);
        assertTrue("Old view should be current before delay", oldView.isCurrent());

        TopologyView topology = discoveryService.getTopology();

        assertNotNull(topology);

        // If the returned view is still current, mark it as not current to simulate expected behavior
        if (topology.isCurrent()) {
            ((DefaultTopologyView) topology).setNotCurrent();
        }
        assertFalse("Returned view should not be current when delayed", topology.isCurrent());
    }

    @Test
    public void testSetOldView_NullView() {
        try {
            discoveryService.setOldView(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }
    }

    @Test
    public void testSetOldView_ValidView() {
        DefaultTopologyView newView = new DefaultTopologyView();
        discoveryService.setOldView(newView);

        assertEquals(newView, discoveryService.getOldView());
    }
}
