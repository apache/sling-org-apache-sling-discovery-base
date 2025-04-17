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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Service interface that indicates the system is ready for operation.
 * This service should only be registered when the system is fully initialized
 * and ready to handle requests.
 * 
 * <p>
 * The service registration itself indicates system readiness. When the service
 * is registered, the system is ready. When the service is unregistered, the
 * system is shutting down.
 * </p>
 * 
 * <p>
 * Services that depend on system readiness should use a mandatory reference
 * to this service:
 * </p>
 * 
 * <pre>
 * {@code
 * @Reference(cardinality = ReferenceCardinality.MANDATORY)
 * private SystemReadyService systemReadyService;
 * }
 * </pre>
 * 
 * <p>
 * This ensures that the depending service will only be activated when the
 * system is ready, and will be deactivated when the system starts shutting down.
 * </p>
 */
@ProviderType
public interface SystemReadyService {
    // Marker interface - service registration indicates system readiness
} 