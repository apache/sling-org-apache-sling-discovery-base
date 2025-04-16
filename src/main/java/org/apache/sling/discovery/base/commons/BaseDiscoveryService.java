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

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.sling.discovery.DiscoveryService;
import org.apache.sling.discovery.InstanceDescription;
import org.apache.sling.discovery.TopologyView;
import org.apache.sling.discovery.base.commons.UndefinedClusterViewException.Reason;
import org.apache.sling.discovery.base.connectors.announcement.AnnouncementRegistry;
import org.apache.sling.discovery.commons.providers.spi.LocalClusterView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.discovery.base.commons.TopologyDelayHandler;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEvent.Type;
import org.apache.sling.discovery.TopologyEventListener;

/**
 * Abstract base class for DiscoveryService implementations which uses the 
 * ClusterViewService plus Topology Connectors to calculate
 * the current TopologyView
 */
public abstract class BaseDiscoveryService implements DiscoveryService {

    private final static Logger logger = LoggerFactory.getLogger(BaseDiscoveryService.class);

    /** the old view previously valid and sent to the TopologyEventListeners **/
    private DefaultTopologyView oldView;

    @Reference
    private TopologyDelayHandler topologyDelayHandler;

    /** The topology event listeners */
    private final Collection<TopologyEventListener> topologyEventListeners = new CopyOnWriteArrayList<>();

    protected abstract ClusterViewService getClusterViewService();
    
    protected abstract AnnouncementRegistry getAnnouncementRegistry();
    
    protected abstract void handleIsolatedFromTopology();
    
    protected DefaultTopologyView getOldView() {
        return oldView;
    }
    
    protected void setOldView(DefaultTopologyView view) {
        if (view==null) {
            throw new IllegalArgumentException("view must not be null");
        }
        logger.debug("setOldView: oldView is now: {}", oldView);
        oldView = view;
    }
    
    /**
     * @see DiscoveryService#getTopology()
     */
    public TopologyView getTopology() {
        // create a new topology view
        final DefaultTopologyView topology = new DefaultTopologyView();

        LocalClusterView localClusterView = null;
        try {
            ClusterViewService clusterViewService = getClusterViewService();
            if (clusterViewService == null) {
                throw new UndefinedClusterViewException(
                        Reason.REPOSITORY_EXCEPTION,
                        "no ClusterViewService available at the moment");
            }
            localClusterView = clusterViewService.getLocalClusterView();
            topology.setLocalClusterView(localClusterView);
        } catch (UndefinedClusterViewException e) {
            // SLING-5030 : when we're cut off from the local cluster we also
            // treat it as being cut off from the entire topology, ie we don't
            // update the announcements but just return
            // the previous oldView marked as !current
            logger.info("getTopology: undefined cluster view: "+e.getReason()+"] "+e);
            oldView.setNotCurrent();
            if (e.getReason()==Reason.ISOLATED_FROM_TOPOLOGY) {
                handleIsolatedFromTopology();
            }
            return oldView;
        }

        Collection<InstanceDescription> attachedInstances = getAnnouncementRegistry()
                .listInstances(localClusterView);
        topology.addInstances(attachedInstances);

        // Check if topology changes should be delayed
        if (topologyDelayHandler != null && topologyDelayHandler.shouldDelayTopologyChange(null)) {
            logger.debug("getTopology: topology changes are delayed, returning old view");
            return oldView;
        }

        return topology;
    }

    protected void handleTopologyEvent(TopologyEvent event) {
        if (event == null) {
            return;
        }

        if (topologyDelayHandler != null) {
            if (topologyDelayHandler.shouldDelayTopologyChange(event)) {
                logger.debug("handleTopologyEvent: delaying topology event: {}", event);
                return;
            }

            if (event.getType() == Type.TOPOLOGY_CHANGING) {
                topologyDelayHandler.startTopologyChange();
            } else if (event.getType() == Type.TOPOLOGY_CHANGED) {
                topologyDelayHandler.endTopologyChange();
            }
        }

        // Update old view when topology changes
        if (event.getType() == Type.TOPOLOGY_CHANGED && event.getNewView() != null) {
            setOldView((DefaultTopologyView) event.getNewView());
        }

        // Notify topology event listeners
        Collection<TopologyEventListener> listeners = getTopologyEventListeners();
        if (listeners != null) {
            for (TopologyEventListener listener : listeners) {
                try {
                    listener.handleTopologyEvent(event);
                } catch (Exception e) {
                    logger.error("Error notifying topology event listener", e);
                }
            }
        }
    }

    protected Collection<TopologyEventListener> getTopologyEventListeners() {
        return topologyEventListeners;
    }

}
