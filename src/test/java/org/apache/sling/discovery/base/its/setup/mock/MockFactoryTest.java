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
package org.apache.sling.discovery.base.its.setup.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class MockFactoryTest {

    /**
     * Regression test: activation code that calls bundleContext.getBundle(0)
     * must not trigger a jMock "unexpected invocation" error.
     */
    @Test
    public void testMockBundleContextAllowsGetBundle0() {
        BundleContext bc = MockFactory.mockBundleContext();
        Bundle systemBundle = bc.getBundle(0);
        assertNotNull(systemBundle);
        assertEquals(Bundle.ACTIVE, systemBundle.getState());
    }
}
