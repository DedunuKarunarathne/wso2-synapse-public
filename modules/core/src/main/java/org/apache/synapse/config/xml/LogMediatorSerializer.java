/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.config.xml;

import org.apache.axiom.om.OMElement;
import org.apache.commons.lang3.StringUtils;
import org.apache.synapse.Mediator;
import org.apache.synapse.mediators.builtin.LogMediator;

/**
 * <pre>
 * &lt;log [level="simple|headers|full|custom"] [separator="string"] [category="INFO|TRACE|DEBUG|WARN|ERROR|FATAL"]&gt;
 *      &lt;message&gt;String template&lt;/message&gt;
 *      &lt;property&gt; *
 * &lt;/log&gt;
 * </pre>
 */
public class LogMediatorSerializer extends AbstractMediatorSerializer {

    public OMElement serializeSpecificMediator(Mediator m) {

        if (!(m instanceof LogMediator)) {
            handleException("Unsupported mediator passed in for serialization : " + m.getType());
        }

        LogMediator mediator = (LogMediator) m;
        OMElement log = fac.createOMElement("log", synNS);
        saveTracingState(log,mediator);

        String logLevel = "";
        switch (mediator.getLogLevel()) {
            case LogMediator.CUSTOM:
                logLevel = "custom";
                break;
            case LogMediator.HEADERS:
                logLevel = "headers";
                break;
            case LogMediator.FULL:
                logLevel = "full";
                break;
            case LogMediator.MESSAGE_TEMPLATE:
                OMElement messageElement = fac.createOMElement("message", synNS);
                messageElement.setText(mediator.getMessageTemplate());
                log.addChild(messageElement);
                log.addAttribute(fac.createOMAttribute("logMessageID", nullNS,
                        Boolean.toString(mediator.isLogMessageID())));
                break;
        }
        if (StringUtils.isNotBlank(logLevel)) {
            log.addAttribute(fac.createOMAttribute("level", nullNS, logLevel));
        }

        if (mediator.getCategory() != LogMediator.CATEGORY_INFO) {
            log.addAttribute(fac.createOMAttribute(
                "category", nullNS,
                    mediator.getCategory() == LogMediator.CATEGORY_TRACE ?
                            LogMediatorFactory.CAT_TRACE :
                    mediator.getCategory() == LogMediator.CATEGORY_DEBUG ?
                            LogMediatorFactory.CAT_DEBUG :
                    mediator.getCategory() == LogMediator.CATEGORY_WARN ?
                            LogMediatorFactory.CAT_WARN :
                    mediator.getCategory() == LogMediator.CATEGORY_ERROR ?
                            LogMediatorFactory.CAT_ERROR :
                    mediator.getCategory() == LogMediator.CATEGORY_FATAL ?
                            LogMediatorFactory.CAT_FATAL :
                            LogMediatorFactory.CAT_INFO
                ));
        }

        if (!LogMediator.DEFAULT_SEP.equals(mediator.getSeparator())) {
            log.addAttribute(fac.createOMAttribute(
                    "separator", nullNS, mediator.getSeparator()));
        }

        super.serializeProperties(log, mediator.getProperties());

        serializeComments(log, mediator.getCommentsList());

        return log;
    }

    public String getMediatorClassName() {
        return LogMediator.class.getName();
    }
}
