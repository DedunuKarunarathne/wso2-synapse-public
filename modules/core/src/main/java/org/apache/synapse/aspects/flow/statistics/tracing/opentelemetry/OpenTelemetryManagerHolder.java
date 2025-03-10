/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.management.TelemetryConstants;
import org.apache.synapse.aspects.flow.statistics.tracing.opentelemetry.management.OpenTelemetryManager;
import org.apache.synapse.config.SynapsePropertiesLoader;

/**
 * Holds the OpenTelemetry Manager, and configurations related to it.
 */
public class OpenTelemetryManagerHolder {

    private static Log logger = LogFactory.getLog(OpenTelemetryManagerHolder.class);
    private static boolean isCollectingPayloads;
    private static boolean isCollectingProperties;
    private static boolean isCollectingVariables;
    private static OpenTelemetryManager openTelemetryManager;

    /**
     * Prevents Instantiation.
     */
    private OpenTelemetryManagerHolder() {}

    /**
     * Loads Tracing configurations by creating an instance of the given type required for the OpenTelemetryManager.
     */
    public static void loadTracerConfigurations() {

        String classpath = SynapsePropertiesLoader.getPropertyValue(TelemetryConstants.OPENTELEMETRY_CLASS,
                TelemetryConstants.DEFAULT_OPENTELEMETRY_CLASS);
        try {
            openTelemetryManager = (OpenTelemetryManager) Class.forName(classpath).newInstance();
            openTelemetryManager.init();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SynapseException e) {
            logger.error("Failed to initialize OpenTelemetryManager for class name: " + classpath, e);
        }
    }

    /**
     * Sets flags that denote whether to collect payloads and properties.
     *
     * @param collectPayloads   Whether to collect payloads
     * @param collectProperties Whether to collect properties
     * @param collectVariables  Whether to collect variables
     */
    public static void setCollectingFlags(boolean collectPayloads, boolean collectProperties, boolean collectVariables) {
        isCollectingPayloads = collectPayloads;
        isCollectingProperties = collectProperties;
        isCollectingVariables = collectVariables;
    }

    public static boolean isCollectingPayloads() {
        return isCollectingPayloads;
    }

    public static boolean isCollectingProperties() {
        return isCollectingProperties;
    }

    public static boolean isCollectingVariables() {
        return isCollectingVariables;
    }

    public static OpenTelemetryManager getOpenTelemetryManager() {
        return openTelemetryManager;
    }
}
