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

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.mediators.template.TemplateMediator;
import org.apache.synapse.mediators.template.TemplateParam;
import org.apache.synapse.util.CommentListUtil;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
/**
 * Factory class for Template configuration as follows
 * <template name="simple_func">
	    <parameter name="p1"/>
        <parameter name="p2"/>*
        <mediator/>+
    </template>
 */
public class TemplateMediatorFactory extends AbstractListMediatorFactory {
    private static final QName TEMPLATE_Q
            = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "template");
    private static final QName TEMPLATE_BODY_Q
            = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "sequence");

    /**
     * Element  QName Definitions
     */
    public static final QName PARAMETER_Q = new QName(
            XMLConfigConstants.SYNAPSE_NAMESPACE, "parameter");


    protected Mediator createSpecificMediator(OMElement elem, Properties properties) {
        TemplateMediator templateTemplateMediator = new TemplateMediator();
        OMAttribute nameAttr = elem.getAttribute(ATT_NAME);
        if (nameAttr != null) {
            Boolean isConnectorTemplate = properties != null &&
                    Boolean.parseBoolean(properties.getProperty(SynapseConstants.CONNECTOR_ARTIFACT));
            if (Boolean.TRUE.equals(isConnectorTemplate)) {
                // Since we use a fully qualified connector package name as a prefix, we need to skip adding
                // the prefix again.
                templateTemplateMediator.setName(nameAttr.getAttributeValue());
            } else {
                templateTemplateMediator.setName(FactoryUtils.getFullyQualifiedName(properties, nameAttr.getAttributeValue()));
            }
            processAuditStatus(templateTemplateMediator, elem);
            initParameters(elem, templateTemplateMediator);
            OMElement templateBodyElem = elem.getFirstChildWithName(TEMPLATE_BODY_Q);
            addChildren(templateBodyElem, templateTemplateMediator, properties);
        } else {
            String msg = "A EIP template should be a named mediator .";
            log.error(msg);
            throw new SynapseException(msg);
        }

        OMAttribute onErrorAttr = elem.getAttribute(ATT_ONERROR);
        if (onErrorAttr != null) {
            templateTemplateMediator.setErrorHandler(onErrorAttr.getAttributeValue());
        }
        CommentListUtil.populateComments(elem, templateTemplateMediator.getCommentsList());
        return templateTemplateMediator;
    }

    private void initParameters(OMElement templateElem, TemplateMediator templateMediator) {
        Iterator subElements = templateElem.getChildElements();
        Collection<TemplateParam> templateParams = new ArrayList<>();
        while (subElements.hasNext()) {
            OMElement child = (OMElement) subElements.next();
            if (child.getQName().equals(PARAMETER_Q)) {
                String paramName = null;
                boolean isParamMandatory = false;
                Object defaultValue = null;
                String description = null;
                OMAttribute paramNameAttr = child.getAttribute(ATT_NAME);
                if (paramNameAttr != null) {
                    paramName = paramNameAttr.getAttributeValue();
                }
                OMAttribute paramMandatoryAttr = child.getAttribute(ATT_IS_MANDATORY);
                if (paramMandatoryAttr != null) {
                    isParamMandatory = Boolean.parseBoolean(paramMandatoryAttr.getAttributeValue());
                }
                OMAttribute paramDefaultValueAttr = child.getAttribute(ATT_DEFAULT_VALUE);
                if (paramDefaultValueAttr != null) {
                    defaultValue = paramDefaultValueAttr.getAttributeValue();
                }
                OMAttribute descriptionAttr = child.getAttribute(ATT_DESCRIPTION);
                if (descriptionAttr != null) {
                    description = descriptionAttr.getAttributeValue();
                }
                templateParams.add(new TemplateParam(paramName, isParamMandatory, defaultValue, description));
//                child.detach();
            }
        }
        templateMediator.setParameters(templateParams);
    }

    public QName getTagQName() {
        return TEMPLATE_Q;
    }

}
