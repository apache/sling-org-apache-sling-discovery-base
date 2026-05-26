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
package org.apache.sling.discovery.base.its.setup;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

public class WithholdingAppender extends AppenderBase<ILoggingEvent> {

    private final ByteArrayOutputStream baos;
    private final Writer writer;
    private PatternLayoutEncoder encoder;

    /**
     * Install the WithholdingAppender, essentially muting all logging 
     * and withholding it until release() is called
     * @return the WithholdingAppender that can be used to get the 
     * withheld log output
     */
    public static WithholdingAppender install() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        // Remove all existing appenders
        rootLogger.detachAndStopAllAppenders();

        final WithholdingAppender withholdingAppender = new WithholdingAppender();

        // Create and configure the encoder
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{dd.MM.yyyy HH:mm:ss.SSS} *%-5p* [%t] %c{1}: %m%n");
        encoder.setContext(loggerContext);
        encoder.start();

        withholdingAppender.setEncoder(encoder);
        withholdingAppender.setContext(loggerContext);
        withholdingAppender.start();

        rootLogger.addAppender(withholdingAppender);
        rootLogger.setLevel(ch.qos.logback.classic.Level.TRACE);

        return withholdingAppender;
    }

    /**
     * Release this WithholdingAppender and optionally dump what was
     * withheld (eg in case of an exception)
     * @param dumpToSysout
     */
    public void release(boolean dumpToSysout) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Stop and remove this appender
        this.stop();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(this);

        // Reset to default configuration
        loggerContext.reset();

        if (dumpToSysout) {
            String withheldLogoutput = getBuffer();
            System.out.println(withheldLogoutput);
        }
    }

    public WithholdingAppender() {
        this.baos = new ByteArrayOutputStream();
        this.writer = new BufferedWriter(new OutputStreamWriter(baos));
    }

    public void setEncoder(PatternLayoutEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (encoder == null) {
            return;
        }
        try {
            byte[] bytes = encoder.encode(event);
            writer.write(new String(bytes));
        } catch (IOException e) {
            // Ignore
        }
    }

    public String getBuffer() {
        try {
            writer.flush();
        } catch (IOException e) {
            // ignore
        }
        return baos.toString();
    }
}
