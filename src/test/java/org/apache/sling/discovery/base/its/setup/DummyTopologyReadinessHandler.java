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
package org.apache.sling.discovery.base.its.setup;

import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.discovery.base.connectors.BaseConfig;
import org.apache.sling.discovery.base.connectors.announcement.AnnouncementRegistry;
import org.apache.sling.discovery.base.connectors.ping.ConnectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.sling.discovery.base.commons.TopologyReadinessHandler;
import org.apache.sling.discovery.base.commons.ClusterViewService;

public class DummyTopologyReadinessHandler extends TopologyReadinessHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String slingId;
    private final ClusterViewService clusterViewService;
    private final AnnouncementRegistry announcementRegistry;
    private final ResourceResolverFactory resourceResolverFactory;
    private final BaseConfig config;
    private final ConnectorRegistry connectorRegistry;
    private final Scheduler scheduler;

    public static DummyTopologyReadinessHandler testConstructor(
            String slingId,
            ClusterViewService clusterViewService,
            AnnouncementRegistry announcementRegistry,
            ResourceResolverFactory resourceResolverFactory,
            BaseConfig config,
            ConnectorRegistry connectorRegistry,
            Scheduler scheduler) {
        DummyTopologyReadinessHandler handler = new DummyTopologyReadinessHandler(slingId, clusterViewService, announcementRegistry,
                resourceResolverFactory, config, connectorRegistry, scheduler);
        handler.activate(null); // Start in STARTUP state
        return handler;
    }

    protected DummyTopologyReadinessHandler(
            String slingId,
            ClusterViewService clusterViewService,
            AnnouncementRegistry announcementRegistry,
            ResourceResolverFactory resourceResolverFactory,
            BaseConfig config,
            ConnectorRegistry connectorRegistry,
            Scheduler scheduler) {
        this.slingId = slingId;
        this.clusterViewService = clusterViewService;
        this.announcementRegistry = announcementRegistry;
        this.resourceResolverFactory = resourceResolverFactory;
        this.config = config;
        this.connectorRegistry = connectorRegistry;
        this.scheduler = scheduler;
    }

    @Override
    public void bindSystemReady(SystemReady systemReady) {
        logger.debug("bindSystemReady: System is ready");
        super.bindSystemReady(systemReady); // This transitions to READY state
    }

    @Override
    public void unbindSystemReady(SystemReady systemReady) {
        logger.debug("unbindSystemReady: System is no longer ready");
        super.unbindSystemReady(systemReady); // This transitions to SHUTDOWN state
    }

    @Override
    public boolean shouldTriggerTopologyChanging() {
        return super.shouldTriggerTopologyChanging();
    }
}
