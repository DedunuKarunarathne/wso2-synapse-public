/*
 *
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.synapse.config;

import java.util.Collection;

import org.apache.synapse.api.API;

import junit.framework.TestCase;
import org.apache.synapse.endpoints.HTTPEndpoint;
import org.apache.synapse.registry.url.SimpleURLRegistry;
import org.apache.synapse.registry.url.SimpleURLRegistryTest;

public class SynapseConfigurationTest extends TestCase {

	public void testAPITableOrderWithAddAPI() {
		SynapseConfiguration config = new SynapseConfiguration();
		API api1 = new API("API1", "/context/test");
		API api2 = new API("API2", "/context");
		API api3 = new API("API3", "/context/test/ctx");
		config.addAPI("API1", api1);
		config.addAPI("API2", api2);
		config.addAPI("API3", api3);
		//this should get the api table in descending order with longest at the first
		Collection<API> apis = config.getAPIs();
		API[] apisArray = apis.toArray(new API[apis.size()]);
		//api3 with context /context/test/ctx should be first in the list
		assertEquals("Order is not correct", api3, apisArray[0]);	
	}

	public void testAPITableOrderWithAddAPIwithoutReOrder() {
		SynapseConfiguration config = new SynapseConfiguration();
		API api1 = new API("API1", "/context/test");
		API api2 = new API("API2", "/context");
		API api3 = new API("API3", "/context/test/ctx");
		
		
		config.addAPI("API1", api1, false);
		config.addAPI("API2", api2, false);
		config.addAPI("API3", api3, false);		
		
		//this should get the api table as it is.
		Collection<API> apis = config.getAPIs();
		API[] apisArray = apis.toArray(new API[apis.size()]);
		//api1 with context /context/test/ctx should be first in the list
		assertEquals("Order is not correct before sorting", api1, apisArray[0]);	
		
		//calling explicitly to order the apitable
		config.reconstructAPITable();
		apis = config.getAPIs();
		API[] apisArray2 = apis.toArray(new API[apis.size()]);
		//api3 with context /context/test/ctx should be first in the list
		assertEquals("Order is not correct", api3, apisArray2[0]);	
	}

	/**
	 * Test concurrent access of entry definition.
	 */
	public void testConcurrentAccessOfEntryDefinition() {
		// One thread deletes an endpoint and creates the same endpoint again. This simulates API re-deployment.
		// Another thread tries to get the entry definition of the endpoint. This simulates Send mediator behavior
		// during API invocation.
		SynapseConfiguration config = new SynapseConfiguration();
		HTTPEndpoint endpoint = new HTTPEndpoint();
		endpoint.setName("endpoint1");
		config.addEndpoint("endpoint1", endpoint);

		Thread apiReDeploymentThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Thread apiInvocationThread = new Thread(new Runnable() {
					@Override
					public void run() {
						Entry entry = config.getEntryDefinition("endpoint1");
						assertNull("An Endpoint type Entry does not exist already", entry);
					}
				});
				synchronized (config) {
					config.removeEndpoint("endpoint1");
					apiInvocationThread.start();
					// Wait until the thread is in blocked state.
					while (apiInvocationThread.getState() != Thread.State.BLOCKED) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
					assertEquals("API invocation thread should be in BLOCKED state",
							Thread.State.BLOCKED, apiInvocationThread.getState());
					config.addEndpoint("endpoint1", endpoint);
				}
				try {
					apiInvocationThread.join();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				assertFalse("API invocation thread should not be live", apiInvocationThread.isAlive());
				Entry entry = config.getEntryDefinition("endpoint1");
				assertNull("An Endpoint type Entry does not exist already", entry);
			}
		});
		apiReDeploymentThread.start();
		try {
			apiReDeploymentThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void testGetEntryDefinition() throws Exception {

		String key = "resources:xslt/sample.xslt";
		SynapseConfiguration config = new SynapseConfiguration();
		Entry entry = config.getEntryDefinition(key);
		assertEquals("Key of entry should be transformed.", "gov:mi-resources/xslt/sample.xslt", entry.getKey());
	}
}
