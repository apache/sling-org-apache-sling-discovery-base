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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.hc.api.condition.SystemReady;
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
@Component(service = TopologyReadinessHandler.class, immediate = true)
public class TopologyReadinessHandler {

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

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.STATIC)
    private volatile SystemReady systemReady;

    @Activate
    protected void activate(ComponentContext context) {
        logger.info("TopologyReadinessHandler activated - in STARTUP state");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        logger.info("TopologyReadinessHandler deactivated");
        stateMachine.transitionTo(SystemState.SHUTDOWN);
    }

    public void bindSystemReady(SystemReady service) {
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
    public void initiateShutdown() {
        if (stateMachine.getCurrentState() == SystemState.READY) {
            logger.info("Initiating shutdown process");
            stateMachine.transitionTo(SystemState.SHUTDOWN);
            logger.info("Shutdown completed successfully");
        }
    }

    /**
     * Check if a topology change should be delayed based on system readiness
     * @return true if the change should be delayed, false otherwise
     */
    public boolean shouldDelayTopologyChange() {
        return !stateMachine.isReady();
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
