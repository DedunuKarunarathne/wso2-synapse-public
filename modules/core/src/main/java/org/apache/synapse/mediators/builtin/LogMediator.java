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

package org.apache.synapse.mediators.builtin;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.logging.log4j.ThreadContext;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.commons.CorrelationConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.MediatorProperty;
import org.apache.synapse.util.InlineExpressionUtil;
import org.apache.synapse.util.logging.LoggingUtils;
import org.apache.synapse.util.xpath.SynapseExpression;
import org.jaxen.JaxenException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs the specified message into the configured logger. The log levels specify
 * which attributes would be logged, and is configurable. Additionally custom
 * properties may be defined to the logger, where literal values or expressions
 * could be specified for logging. The custom properties are printed into the log
 * using the defined separator (\n, "," etc)
 */
public class LogMediator extends AbstractMediator {

    /** Only properties specified to the Log mediator */
    public static final int CUSTOM  = 0;
    /** To, From, WSAction, SOAPAction, ReplyTo, MessageID and any properties */
    public static final int SIMPLE  = 1;
    /** All SOAP header blocks and any properties */
    public static final int HEADERS = 2;
    /** all attributes of level 'simple' and the SOAP envelope and any properties */
    public static final int FULL    = 3;

    /** The message template and the additional properties specified to the Log mediator */
    public static final int MESSAGE_TEMPLATE = 4;

    public static final int CATEGORY_INFO = 0;
    public static final int CATEGORY_DEBUG = 1;
    public static final int CATEGORY_TRACE = 2;
    public static final int CATEGORY_WARN = 3;
    public static final int CATEGORY_ERROR = 4;
    public static final int CATEGORY_FATAL = 5;

    public static final String DEFAULT_SEP = ", ";

    /** The default log level is set to SIMPLE */
    private int logLevel = SIMPLE;
    /** The separator for which used to separate logging information */
    private String separator = DEFAULT_SEP;
    /** Category of the log statement */
    private int category = CATEGORY_INFO;
    /** The holder for the custom properties */
    private final List<MediatorProperty> properties = new ArrayList<MediatorProperty>();

    private String messageTemplate = "";
    private boolean isContentAware = false;
    private boolean logMessageID = false;
    private final Map<String, SynapseExpression> inlineExpressionCache = new ConcurrentHashMap<>();

    /**
     * Logs the current message according to the supplied semantics
     *
     * @param synCtx (current) message to be logged
     * @return true always
     */
    public boolean mediate(MessageContext synCtx) {

        Object correlationId = getCorrelationId(synCtx);
        if (correlationId instanceof String) {
            ThreadContext.put(CorrelationConstants.CORRELATION_MDC_PROPERTY, String.valueOf(correlationId));
        }

        if (synCtx.getEnvironment().isDebuggerEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }

        SynapseLog synLog = getLog(synCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : Log mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        if (this.getLogLevel() == MESSAGE_TEMPLATE) {
            // Entry points info should be logged for audit logs only. Hence, the property "logEntryPointInfo"
            // should be set at this point just before the audit logs and removed just after audit logs.
            synCtx.setProperty(LoggingUtils.LOG_ENTRY_POINT_INFO, "true");
        }
        switch (category) {
            case CATEGORY_INFO :
                synLog.auditLog(getLogMessage(synCtx));
                break;
            case CATEGORY_TRACE :
            	if(synLog.isTraceEnabled()){
            		synLog.auditTrace(getLogMessage(synCtx));
            	}
                break;
            case CATEGORY_DEBUG :
            	if(synLog.isDebugEnabled()){
            		synLog.auditDebug(getLogMessage(synCtx));
            	}
                break;
            case CATEGORY_WARN :
                synLog.auditWarn(getLogMessage(synCtx));
                break;
            case CATEGORY_ERROR :
                synLog.auditError(getLogMessage(synCtx));
                break;
            case CATEGORY_FATAL :
                synLog.auditFatal(getLogMessage(synCtx));
                break;
        }

        synCtx.getPropertyKeySet().remove(LoggingUtils.LOG_ENTRY_POINT_INFO);
        synLog.traceOrDebug("End : Log mediator");

        return true;
    }

    private String getLogMessage(MessageContext synCtx) {
        switch (logLevel) {
            case CUSTOM:
            case MESSAGE_TEMPLATE:
                return getCustomLogMessage(synCtx);
            case SIMPLE:
                return getSimpleLogMessage(synCtx);
            case HEADERS:
                return getHeadersLogMessage(synCtx);
            case FULL:
                return getFullLogMessage(synCtx);
            default:
                return "Invalid log level specified";
        }
    }

    private String getCustomLogMessage(MessageContext synCtx) {
        StringBuffer sb = new StringBuffer();
        if (logMessageID) {
            if (synCtx.getMessageID() != null) {
                sb.append("MessageID: ").append(synCtx.getMessageID());
            }
            if (getCorrelationId(synCtx) != null) {
                sb.append(separator).append("correlation_id: ").append(getCorrelationId(synCtx));
            }
            // append separator if message id is logged
            if (sb.length() > 0) {
                sb.append(separator);
            }
        }
        processMessageTemplate(sb, synCtx, messageTemplate);
        setCustomProperties(sb, synCtx);
        return trimLeadingSeparator(sb);
    }

    private String getSimpleLogMessage(MessageContext synCtx) {
        StringBuffer sb = new StringBuffer();
        processMessageTemplate(sb, synCtx, messageTemplate);
        // append separator if the message template is not empty
        if (sb.length() > 0) {
            sb.append(separator);
        }
        if (synCtx.getTo() != null)
            sb.append("To: ").append(synCtx.getTo().getAddress());
        else
            sb.append("To: ");
        if (synCtx.getFrom() != null)
            sb.append(separator).append("From: ").append(synCtx.getFrom().getAddress());
        if (synCtx.getWSAAction() != null)
            sb.append(separator).append("WSAction: ").append(synCtx.getWSAAction());
        if (synCtx.getSoapAction() != null)
            sb.append(separator).append("SOAPAction: ").append(synCtx.getSoapAction());
        if (synCtx.getReplyTo() != null)
            sb.append(separator).append("ReplyTo: ").append(synCtx.getReplyTo().getAddress());
        if (synCtx.getMessageID() != null)
            sb.append(separator).append("MessageID: ").append(synCtx.getMessageID());
        if (getCorrelationId(synCtx) != null)
            sb.append(separator).append("correlation_id: ").append(getCorrelationId(synCtx));
        sb.append(separator).append("Direction: ").append(
                synCtx.isResponse() ? "response" : "request");
        setCustomProperties(sb, synCtx);
        return trimLeadingSeparator(sb);
    }

    private String getHeadersLogMessage(MessageContext synCtx) {
        StringBuffer sb = new StringBuffer();
        processMessageTemplate(sb, synCtx, messageTemplate);
        if (synCtx.getEnvelope() != null) {
            SOAPHeader header = synCtx.getEnvelope().getHeader();
            if (getCorrelationId(synCtx) != null)
                sb.append(" correlation_id : " + getCorrelationId(synCtx));
            if (header != null) {
                for (Iterator iter = header.examineAllHeaderBlocks(); iter.hasNext();) {
                    Object o = iter.next();
                    if (o instanceof SOAPHeaderBlock) {
                        SOAPHeaderBlock headerBlk = (SOAPHeaderBlock) o;
                        sb.append(separator).append(headerBlk.getLocalName()).
                                append(" : ").append(headerBlk.getText());
                    } else if (o instanceof OMElement) {
                        OMElement headerElem = (OMElement) o;
                        sb.append(separator).append(headerElem.getLocalName()).
                                append(" : ").append(headerElem.getText());
                    }
                }
            }
        }
        setCustomProperties(sb, synCtx);
        return trimLeadingSeparator(sb);
    }

    private String getFullLogMessage(MessageContext synCtx) {
        StringBuffer sb = new StringBuffer();
        sb.append(getSimpleLogMessage(synCtx));
        try {
            org.apache.axis2.context.MessageContext a2mc = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
            if (JsonUtil.hasAJsonPayload(a2mc)) {
                sb.append(separator).append("Payload: ").append(JsonUtil.jsonPayloadToString(a2mc));
            } else if (synCtx.getEnvelope() != null) {
                sb.append(separator).append("Envelope: ").append(synCtx.getEnvelope());
            }
        } catch (Exception e) {
            SOAPEnvelope envelope = synCtx.isSOAP11() ? OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope()
                    :OMAbstractFactory.getSOAP12Factory().getDefaultEnvelope();
            try {
                synCtx.setEnvelope(envelope);
            } catch (Exception e1) {
                log.error("Could not replace faulty SOAP Envelop. Error: " + e1.getLocalizedMessage());
                return sb.toString();
            }
            handleException("Could not build full log message: " + e.getLocalizedMessage(), e, synCtx);
        }
        return trimLeadingSeparator(sb);
    }

    private void setCustomProperties(StringBuffer sb, MessageContext synCtx) {
        if (properties != null && !properties.isEmpty()) {
            for (MediatorProperty property : properties) {
                if(property != null){
                sb.append(separator).append(property.getName()).append(" = ").append(property.getValue()
                        != null ? property.getValue() :
                        property.getEvaluatedExpression(synCtx));
                }
            }
        }
    }

    private Object getCorrelationId(MessageContext synCtx) {
        Object correlationId = null;
        if (synCtx instanceof Axis2MessageContext) {
            Axis2MessageContext axis2Ctx = ((Axis2MessageContext) synCtx);
            correlationId = axis2Ctx.getAxis2MessageContext().getProperty(CorrelationConstants.CORRELATION_ID);
        }
        return correlationId;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator.replace("\\n", "\n").replace("\\t", "\t");
    }

    public void addProperty(MediatorProperty p) {
        properties.add(p);
    }

    public void addAllProperties(List<MediatorProperty> list) {
        properties.addAll(list);
        for (MediatorProperty property : properties) {
            if (property.getExpression() != null && property.getExpression().isContentAware()) {
                isContentAware = true;
                return;
            }
        }
    }

    public List<MediatorProperty> getProperties() {
        return properties;
    }

    public int getCategory() {
        return category;
    }

    public void setCategory(int category) {
        if (category > 0 && category <= 5) {
            this.category = category;
        } else {
            
        }
    }

    public String getMessageTemplate() {

        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {

        this.messageTemplate = messageTemplate.replace("\\n", "\n").replace("\\t", "\t");
    }

    public boolean isLogMessageID() {

        return logMessageID;
    }

    public void setLogMessageID(boolean logMessageID) {

        this.logMessageID = logMessageID;
    }

    private String trimLeadingSeparator(StringBuffer sb) {
        String retStr = sb.toString();
        if (retStr.startsWith(separator)) {
            return retStr.substring(separator.length());
        } else {
            return retStr;
        }
    }

    private void processMessageTemplate(StringBuffer stringBuffer, MessageContext synCtx, String template) {
        try {
            stringBuffer.append(InlineExpressionUtil.processInLineSynapseExpressionTemplate(synCtx, template,
                    inlineExpressionCache));
        } catch (JaxenException e) {
            handleException("Failed to process the message template : " + template, e, synCtx);
        }
    }

    @Override
    public boolean isContentAware() {

        if (logLevel == MESSAGE_TEMPLATE || logLevel == CUSTOM) {
            return isContentAware;
        }
        return true;
    }

    public void processTemplateAndSetContentAware() throws JaxenException {

        isContentAware = InlineExpressionUtil.initInlineSynapseExpressions(messageTemplate, inlineExpressionCache);
    }
}
