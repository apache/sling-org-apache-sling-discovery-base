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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.sling.discovery.TopologyEvent;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;

/**
 * Handles topology delay mechanism to ensure system stability during topology changes.
 * This component can be used to delay topology changes until the system is ready.
 */
@Component(service = TopologyDelayHandler.class)
public class TopologyDelayHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicBoolean systemReady = new AtomicBoolean(false);
    private final AtomicLong lastTopologyChangeTime = new AtomicLong(0);
    private final AtomicBoolean topologyChangeInProgress = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean startupInProgress = new AtomicBoolean(true);

    private long delayDuration = 5000; // Default 5 second delay
    private long shutdownTimeout = 30000; // Default 30 second shutdown timeout
    private long startupTimeout = 60000; // Default 60 second startup timeout
    private Thread startupMonitorThread;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile SystemReadyService systemReadyService;

    @Activate
    protected void activate(ComponentContext context) {
        logger.info("TopologyDelayHandler activated");
        shutdownInProgress.set(false);
        startupInProgress.set(true);
        
        // Only start monitor thread if timeout is not disabled (0)
        if (startupTimeout > 0) {
            startupMonitorThread = new Thread(() -> {
                try {
                    Thread.sleep(startupTimeout);
                    if (startupInProgress.get()) {
                        logger.warn("Startup timeout reached, forcing system ready state");
                        startupInProgress.set(false);
                        setSystemReady(true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "TopologyDelayHandler-StartupMonitor");
            startupMonitorThread.start();
        } else {
            // If timeout is disabled, consider startup complete
            startupInProgress.set(false);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        logger.info("TopologyDelayHandler deactivated");
        if (startupMonitorThread != null) {
            startupMonitorThread.interrupt();
        }
        initiateShutdown();
    }

    protected void bindSystemReadyService(SystemReadyService service) {
        logger.debug("SystemReadyService bound");
        this.systemReadyService = service;
        // If we're in startup and the system ready service is available, mark system as ready
        if (startupInProgress.get() && service.isSystemReady()) {
            startupInProgress.set(false);
            setSystemReady(true);
        }
    }

    protected void unbindSystemReadyService(SystemReadyService service) {
        logger.debug("SystemReadyService unbound");
        this.systemReadyService = null;
        // If we're in shutdown and the system ready service is removed, mark system as not ready
        if (shutdownInProgress.get()) {
            setSystemReady(false);
        }
    }

    /**
     * Initiate the shutdown process
     */
    protected void initiateShutdown() {
        if (shutdownInProgress.compareAndSet(false, true)) {
            logger.info("Initiating shutdown process");
            setSystemReady(false);
            
            // If shutdown timeout is disabled, don't wait
            if (shutdownTimeout <= 0) {
                return;
            }
            
            // Wait for running jobs to complete or timeout
            long startTime = System.currentTimeMillis();
            while (topologyChangeInProgress.get() && 
                   (System.currentTimeMillis() - startTime) < shutdownTimeout) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (topologyChangeInProgress.get()) {
                logger.warn("Shutdown timeout reached while waiting for topology changes to complete");
            } else {
                logger.info("Shutdown completed successfully");
            }
        }
    }

    /**
     * Set the shutdown timeout in milliseconds
     * @param timeout the shutdown timeout in milliseconds
     */
    public void setShutdownTimeout(long timeout) {
        this.shutdownTimeout = timeout;
    }

    /**
     * Set the startup timeout in milliseconds
     * @param timeout the startup timeout in milliseconds
     */
    public void setStartupTimeout(long timeout) {
        this.startupTimeout = timeout;
        // If timeout is set to 0, consider startup complete
        if (timeout == 0 && startupInProgress.get()) {
            startupInProgress.set(false);
        }
    }

    /**
     * Set the delay duration in milliseconds
     * @param delayDuration the delay duration in milliseconds
     */
    public void setDelayDuration(long delayDuration) {
        this.delayDuration = delayDuration;
    }

    /**
     * Check if a topology change should be delayed
     * @param event the topology event
     * @return true if the change should be delayed, false otherwise
     */
    public boolean shouldDelayTopologyChange(TopologyEvent event) {
        if (event == null) {
            return false;
        }

        // If startup is in progress, delay all topology changes
        if (startupInProgress.get()) {
            logger.debug("Startup in progress, delaying topology change");
            return true;
        }

        // If shutdown is in progress, delay all topology changes
        if (shutdownInProgress.get()) {
            logger.debug("Shutdown in progress, delaying topology change");
            return true;
        }

        // If system is not ready and we have a system ready service, delay
        if (systemReadyService != null && !systemReady.get()) {
            logger.debug("System not ready, delaying topology change");
            return true;
        }

        // If we're in the middle of a topology change, delay
        if (topologyChangeInProgress.get()) {
            logger.debug("Topology change in progress, delaying new change");
            return true;
        }

        // If we're within the delay period from the last change, delay
        long now = System.currentTimeMillis();
        if (now - lastTopologyChangeTime.get() < delayDuration) {
            logger.debug("Within delay period from last change, delaying topology change");
            return true;
        }

        return false;
    }

    /**
     * Mark the start of a topology change
     */
    public void startTopologyChange() {
        topologyChangeInProgress.set(true);
        lastTopologyChangeTime.set(System.currentTimeMillis());
    }

    /**
     * Mark the end of a topology change
     */
    public void endTopologyChange() {
        topologyChangeInProgress.set(false);
    }

    /**
     * Set the system ready state
     * @param ready true if the system is ready
     */
    public void setSystemReady(boolean ready) {
        systemReady.set(ready);
    }

    /**
     * Check if the system is ready
     * @return true if the system is ready
     */
    public boolean isSystemReady() {
        return systemReady.get();
    }
} 
