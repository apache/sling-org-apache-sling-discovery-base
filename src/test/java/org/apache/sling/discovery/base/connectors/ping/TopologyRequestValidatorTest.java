/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.discovery.base.connectors.ping;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicHeader;
import org.apache.sling.discovery.base.connectors.BaseConfig;
import org.apache.sling.discovery.base.its.setup.mock.SimpleConnectorConfig;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TopologyRequestValidatorTest {
    
    private TopologyRequestValidator topologyRequestValidator;


    @Before
    public void before() throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
        BaseConfig config= new SimpleConnectorConfig();
        setPrivate(config, "sharedKey", "testKey");
        setPrivate(config, "hmacEnabled", true);
        setPrivate(config, "encryptionEnabled", true);
        setPrivate(config, "keyInterval", 3600*100*4);
        topologyRequestValidator = new TopologyRequestValidator(config);
    }
    
    private void setPrivate(Object o, String field, Object value) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = o.getClass().getDeclaredField(field);
        if ( !f.isAccessible()) {
            f.setAccessible(true);
        }
        f.set(o, value);
    }

    @Test
    public void testTrustRequest() throws IOException {
        final HttpPut method = new HttpPut("/TestUri");
        String clearMessage = "TestMessage";
        final String message = topologyRequestValidator.encodeMessage(clearMessage);
        Assert.assertNotNull(message);
        Assert.assertNotEquals(message, clearMessage);
        topologyRequestValidator.trustMessage(method, message);
        
        Assert.assertNotNull(method.getFirstHeader(TopologyRequestValidator.HASH_HEADER));
        Assert.assertNotNull(method.getFirstHeader(TopologyRequestValidator.HASH_HEADER).getValue());
        Assert.assertTrue(method.getFirstHeader(TopologyRequestValidator.HASH_HEADER).getValue().length() > 0);
        Assert.assertNotNull(method.getFirstHeader(TopologyRequestValidator.SIG_HEADER));
        Assert.assertNotNull(method.getFirstHeader(TopologyRequestValidator.SIG_HEADER).getValue());
        Assert.assertTrue(method.getFirstHeader(TopologyRequestValidator.SIG_HEADER).getValue().length() > 0);
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(TopologyRequestValidator.HASH_HEADER))
            .thenReturn(method.getFirstHeader(TopologyRequestValidator.HASH_HEADER).getValue());
        when(request.getHeader(TopologyRequestValidator.SIG_HEADER))
            .thenReturn(method.getFirstHeader(TopologyRequestValidator.SIG_HEADER).getValue());
        when(request.getHeader("Content-Encoding"))
            .thenReturn("");
        when(request.getRequestURI())
            .thenReturn(method.getURI().getPath());
        when(request.getReader())
            .thenReturn(new BufferedReader(new StringReader(message)));
        
        Assert.assertTrue(topologyRequestValidator.isTrusted(request));
        Assert.assertEquals(clearMessage, topologyRequestValidator.decodeMessage(request));
    }
    
    
    
    @Test
    public void testTrustResponse() throws IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/Test/Uri2");

        final HttpServletResponse response = mock(HttpServletResponse.class);
        final Map<Object, Object> headers = new HashMap<Object, Object>();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                headers.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }
        }).when(response).setHeader(anyString(), anyString());

        String clearMessage =  "TestMessage2";
        final String message = topologyRequestValidator.encodeMessage(clearMessage);
        topologyRequestValidator.trustMessage(response, request, message);
        
        final HttpEntity responseEntity = mock(HttpEntity.class);
        when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(message.getBytes()));
        
        final HttpResponse resp = mock(HttpResponse.class);
        when(resp.getFirstHeader(anyString())).thenAnswer(new Answer<BasicHeader>() {
            @Override
            public BasicHeader answer(InvocationOnMock invocation) throws Throwable {
                String headerName = invocation.getArgument(0);
                String headerValue = (String) headers.get(headerName);
                return new BasicHeader(headerName, headerValue);
            }
        });
        when(resp.getEntity()).thenReturn(responseEntity);
        
        topologyRequestValidator.isTrusted(resp);
        topologyRequestValidator.decodeMessage("/Test/Uri2", resp);
        
    }
    
    

}
