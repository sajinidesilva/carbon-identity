/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.identity.provisioning.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.wso2.carbon.identity.application.common.ProvisioningConnectorService;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.ProvisioningConnectorConfig;
import org.wso2.carbon.identity.application.mgt.listener.ApplicationMgtListener;
import org.wso2.carbon.identity.provisioning.AbstractProvisioningConnectorFactory;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;
import org.wso2.carbon.identity.provisioning.listener.ApplicationMgtProvisioningListener;
import org.wso2.carbon.identity.provisioning.listener.DefaultInboundUserProvisioningListener;
import org.wso2.carbon.identity.provisioning.listener.IdentityProviderMgtProvisioningListener;
import org.wso2.carbon.idp.mgt.listener.IdentityProviderMgtLister;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.core.listener.UserOperationEventListener;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.HashMap;
import java.util.Map;

/**
 * @scr.component name=
 * "org.wso2.carbon.identity.provision.internal.IdentityProvisionServiceComponent"
 * immediate="true"
 * @scr.reference name="registry.service"
 * interface="org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService"
 * unbind="unsetRegistryService"
 * @scr.reference name="realm.service" interface="org.wso2.carbon.user.core.service.RealmService"
 * cardinality="1..1" policy="dynamic" bind="setRealmService"
 * unbind="unsetRealmService"
 */
public class IdentityProvisionServiceComponent {

    private static Log log = LogFactory.getLog(IdentityProvisionServiceComponent.class);

    private static RealmService realmService;
    private static RegistryService registryService;
    private static BundleContext bundleContext;
    private static Map<String, AbstractProvisioningConnectorFactory> connectorFactories = new HashMap<String, AbstractProvisioningConnectorFactory>();

    /**
     * @return
     */
    public static RealmService getRealmService() {
        return realmService;
    }

    /**
     * @param realmService
     */
    protected void setRealmService(RealmService realmService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting the Realm Service");
        }
        IdentityProvisionServiceComponent.realmService = realmService;
    }

    /**
     * @return
     */
    public static RegistryService getRegistryService() {
        return registryService;
    }

    /**
     * @param registryService
     */
    protected void setRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting the Registry Service");
        }
        IdentityProvisionServiceComponent.registryService = registryService;
    }

    /**
     * @return
     */
    public static Map<String, AbstractProvisioningConnectorFactory> getConnectorFactories() {
        return connectorFactories;
    }

    /**
     * @param context
     */
    protected void activate(ComponentContext context) {

        try {
            bundleContext = context.getBundleContext();

            try {
                bundleContext.registerService(UserOperationEventListener.class.getName(), new DefaultInboundUserProvisioningListener(), null);
                if (log.isDebugEnabled()) {
                    log.debug("Identity Provision Event listener registered successfully");
                }
                bundleContext.registerService(ApplicationMgtListener.class.getName(), new ApplicationMgtProvisioningListener(), null);
                if (log.isDebugEnabled()) {
                    log.debug("Application Management Event listener registered successfully");
                }
                bundleContext.registerService(IdentityProviderMgtLister.class.getName(), new IdentityProviderMgtProvisioningListener(), null);
                if (log.isDebugEnabled()) {
                    log.debug("Identity Provider Management Event listener registered successfully");
                }

                ServiceTracker<AbstractProvisioningConnectorFactory, AbstractProvisioningConnectorFactory> authServiceTracker;

                authServiceTracker = new ServiceTracker<AbstractProvisioningConnectorFactory, AbstractProvisioningConnectorFactory>(
                        bundleContext,
                        AbstractProvisioningConnectorFactory.class.getName(),
                        new ServiceTrackerCustomizer<AbstractProvisioningConnectorFactory, AbstractProvisioningConnectorFactory>() {

                            @Override
                            public AbstractProvisioningConnectorFactory addingService(
                                    ServiceReference<AbstractProvisioningConnectorFactory> serviceReference) {
                                AbstractProvisioningConnectorFactory connectorFactory = serviceReference
                                        .getBundle().getBundleContext()
                                        .getService(serviceReference);
                                connectorFactories.put(connectorFactory.getConnectorType(),
                                        connectorFactory);
                                if (log.isDebugEnabled()) {
                                    log.debug("Added provisioning connector : "
                                            + connectorFactory.getConnectorType());
                                }

                                ProvisioningConnectorConfig provisioningConnectorConfig = new ProvisioningConnectorConfig();
                                provisioningConnectorConfig.setName(connectorFactory
                                        .getConnectorType());
                                provisioningConnectorConfig
                                        .setProvisioningProperties(connectorFactory
                                                .getConfigurationProperties().toArray(
                                                        new Property[connectorFactory
                                                                .getConfigurationProperties()
                                                                .size()]));
                                ProvisioningConnectorService.getInstance()
                                        .addProvisioningConnectorConfigs(
                                                provisioningConnectorConfig);

                                return connectorFactory;
                            }

                            @Override
                            public void modifiedService(
                                    ServiceReference<AbstractProvisioningConnectorFactory> serviceReference,
                                    AbstractProvisioningConnectorFactory service) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Modified provisioning connector : "
                                            + service.getConnectorType());
                                }
                            }

                            @Override
                            public void removedService(
                                    ServiceReference<AbstractProvisioningConnectorFactory> serviceReference,
                                    AbstractProvisioningConnectorFactory service) {
                                connectorFactories.remove(service);
                                serviceReference.getBundle().getBundleContext()
                                        .ungetService(serviceReference);

                                ProvisioningConnectorConfig provisioningConnectorConfig = ProvisioningConnectorService.getInstance().getProvisioningConnectorByName(service.getConnectorType());
                                ProvisioningConnectorService.getInstance().removeProvisioningConnectorConfigs(provisioningConnectorConfig);

                                if (log.isDebugEnabled()) {
                                    log.debug("Removed provisioning connector : "
                                            + service.getConnectorType());
                                }
                            }

                        });
                authServiceTracker.open();

                if (log.isDebugEnabled()) {
                    log.debug("IdentityProvisioningConnector service tracker started successfully");
                }

            } catch (IdentityProvisioningException e) {
                log.error("Error while initiating identity provisioning connector framework", e);
            }

            if (log.isDebugEnabled()) {
                log.debug("Identity Provisioning framework bundle is activated");
            }
        } catch (Exception e) {
            log.error("Error while activating Identity Provision bundle", e);
        }
    }

    /**
     * @param context
     */
    protected void deactivate(ComponentContext context) {
        if (log.isDebugEnabled()) {
            log.debug("Identity Provision bundle is de-activated");
        }
    }

    /**
     * @param registryService
     */
    protected void unsetRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("UnSetting the Registry Service");
        }
        IdentityProvisionServiceComponent.registryService = null;
    }

    /**
     * @param realmService
     */
    protected void unsetRealmService(RealmService realmService) {
        if (log.isDebugEnabled()) {
            log.debug("UnSetting the Realm Service");
        }
        IdentityProvisionServiceComponent.realmService = null;
    }
}
