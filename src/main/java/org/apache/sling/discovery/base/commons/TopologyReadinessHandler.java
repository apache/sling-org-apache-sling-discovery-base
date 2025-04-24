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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.hc.api.condition.SystemReady;
import org.apache.sling.discovery.DiscoveryService;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.apache.sling.discovery.TopologyView;
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
 * Coordinates topology changes based on system readiness state.
 * This component ensures that topology changes only occur when the system is in a stable state,
 * both during startup and shutdown sequences.
 * 
 * The handler manages three main states:
 * 1. STARTUP: Initial state when the system is starting up
 * 2. READY: System is ready for normal operation and topology changes
 * 3. SHUTDOWN: System is in the process of shutting down
 * 
 * State Transitions:
 * - System starts in STARTUP state
 * - Transitions to READY state only when SystemReady service is bound
 * - Transitions to SHUTDOWN state when:
 *   * SystemReady service is unbound
 *   * Component is deactivated
 * 
 * Note: This component requires the Felix SystemReady service to function properly.
 * The system will remain in STARTUP state until SystemReady service is bound,
 * and will transition to SHUTDOWN state when the service is unbound.
 */
@Component(service = {TopologyReadinessHandler.class, TopologyEventListener.class},
        immediate = true,
        property = {
            "service.description=Topology Readiness Handler",
            "service.vendor=The Apache Software Foundation"}
)
public class TopologyReadinessHandler implements TopologyEventListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Represents the possible states of the system with explicit transitions.
     */
    private enum SystemState {
        STARTUP {
            @Override
            protected SystemState[] getAllowedTransitions() {
                return new SystemState[] { READY };
            }
        },
        READY {
            @Override
            protected SystemState[] getAllowedTransitions() {
                return new SystemState[] { SHUTDOWN };
            }
        },
        SHUTDOWN {
            @Override
            protected SystemState[] getAllowedTransitions() {
                return new SystemState[] { };  // No transitions allowed from SHUTDOWN
            }
        };

        protected abstract SystemState[] getAllowedTransitions();
    }

    private final StateMachine stateMachine = new StateMachine();
    private final AtomicBoolean topologyChangeInProgress = new AtomicBoolean(false);
    private final AtomicLong lastTopologyChangeTime = new AtomicLong(0);
    private long delayDuration = 5000; // Default 5 second delay between topology changes
    private long shutdownTimeout = 30000; // Default 30 second shutdown timeout

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.STATIC)
    private volatile SystemReady systemReady;

    @Reference
    private DiscoveryService discoveryService;

    @Activate
    protected void activate(ComponentContext context) {
        logger.info("TopologyReadinessHandler activated - in STARTUP state");
        // State is already STARTUP by default
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        logger.info("TopologyReadinessHandler deactivated");
        stateMachine.transitionTo(SystemState.SHUTDOWN);
    }

    protected void bindSystemReady(SystemReady service) {
        logger.debug("SystemReady service bound - transitioning to READY state");
        stateMachine.transitionTo(SystemState.READY);
    }

    protected void unbindSystemReady(SystemReady service) {
        logger.debug("SystemReady service unbound - transitioning to SHUTDOWN state");
        stateMachine.transitionTo(SystemState.SHUTDOWN);
    }

    /**
     * Initiate the shutdown process
     */
    protected void initiateShutdown() {
        if (stateMachine.getCurrentState() == SystemState.READY) {
            logger.info("Initiating shutdown process");
            
            // Mark current view as not current
            if (discoveryService != null) {
                TopologyView currentView = discoveryService.getTopology();
                if (currentView instanceof DefaultTopologyView) {
                    logger.info("Marking current topology view as not current during shutdown");
                    ((DefaultTopologyView) currentView).setNotCurrent();
                }
            }
            
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
     * Set the delay duration in milliseconds
     * @param delayDuration the delay duration in milliseconds
     */
    public void setDelayDuration(long delayDuration) {
        this.delayDuration = delayDuration;
    }

    /**
     * Check if a topology change should be delayed based on system readiness
     * @param event the topology event
     * @return true if the change should be delayed, false otherwise
     */
    public boolean shouldDelayTopologyChange(TopologyEvent event) {
        if (event == null) {
            return false;
        }

        // If system is not READY, delay all topology changes
        if (!stateMachine.isReady()) {
            logger.debug("System in {} state, delaying topology change", 
                stateMachine.getCurrentState());
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

    @Override
    public void handleTopologyEvent(TopologyEvent event) {
        if (event == null) {
            return;
        }

        if (shouldDelayTopologyChange(event)) {
            logger.debug("handleTopologyEvent: delaying topology event: {}", event);
            return;
        }

        switch (event.getType()) {
            case TOPOLOGY_CHANGING:
                startTopologyChange();
                break;
            case TOPOLOGY_CHANGED:
                endTopologyChange();
                break;
            default:
                break;
        }
    }

    private final class StateMachine {
        private final AtomicReference<SystemState> currentState = new AtomicReference<>(SystemState.STARTUP);

        public void transitionTo(SystemState newState) {
            SystemState current = currentState.get();
            if (!isValidTransition(current, newState)) {
                throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s", current, newState)
                );
            }
            
            if (currentState.compareAndSet(current, newState)) {
                logger.info("System state transitioned from {} to {}", current, newState);
            }
        }

        private boolean isValidTransition(SystemState from, SystemState to) {
            for (SystemState allowed : from.getAllowedTransitions()) {
                if (allowed == to) return true;
            }
            return false;
        }

        public SystemState getCurrentState() {
            return currentState.get();
        }

        public boolean isReady() {
            return getCurrentState() == SystemState.READY;
        }
    }

}
