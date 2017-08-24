/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.policy;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.store.ObjectStoreManager.BASE_IN_MEMORY_OBJECT_STORE_KEY;
import static org.mule.runtime.api.store.ObjectStoreManager.BASE_PERSISTENT_OBJECT_STORE_KEY;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCK_PROVIDER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_POLICY_MANAGER_STATE_HANDLER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STORE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_TIME_SUPPLIER;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.module.deployment.impl.internal.artifact.ArtifactContextBuilder.newBuilder;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.policy.DefaultPolicyInstance;
import org.mule.runtime.core.api.policy.Policy;
import org.mule.runtime.core.api.policy.PolicyInstance;
import org.mule.runtime.core.api.policy.PolicyParametrization;
import org.mule.runtime.core.api.policy.PolicyPointcut;
import org.mule.runtime.core.api.registry.MuleRegistry;
import org.mule.runtime.core.api.registry.RegistrationException;
import org.mule.runtime.deployment.model.api.application.Application;
import org.mule.runtime.deployment.model.api.artifact.ArtifactContext;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPlugin;
import org.mule.runtime.deployment.model.api.policy.PolicyTemplate;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderRepository;
import org.mule.runtime.module.deployment.impl.internal.artifact.ArtifactContextBuilder;
import org.mule.runtime.module.deployment.impl.internal.artifact.CompositeArtifactExtensionManagerFactory;
import org.mule.runtime.module.extension.api.manager.DefaultExtensionManagerFactory;
import org.mule.runtime.module.extension.internal.loader.ExtensionModelLoaderRepository;
import org.mule.runtime.module.service.ServiceRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link ApplicationPolicyInstance} that depends on a {@link PolicyTemplate} artifact.
 */
public class DefaultApplicationPolicyInstance implements ApplicationPolicyInstance {

  private final Application application;
  private final PolicyTemplate template;
  private final PolicyParametrization parametrization;
  private final ServiceRepository serviceRepository;
  private final ClassLoaderRepository classLoaderRepository;
  private final List<ArtifactPlugin> artifactPlugins;
  private final ExtensionModelLoaderRepository extensionModelLoaderRepository;
  private ArtifactContext policyContext;
  private PolicyInstance policyInstance;

  /**
   * Creates a new policy instance
   *
   * @param application application artifact owning the created policy. Non null
   * @param template policy template from which the instance will be created. Non null
   * @param parametrization parameters used to configure the created instance. Non null
   * @param serviceRepository repository of available services. Non null.
   * @param classLoaderRepository contains the registered classloaders that can be used to load serialized classes. Non null.
   * @param artifactPlugins artifact plugins deployed inside the policy. Non null.
   * @param extensionModelLoaderRepository {@link ExtensionModelLoaderRepository} with the available extension loaders. Non null.
   */
  public DefaultApplicationPolicyInstance(Application application, PolicyTemplate template,
                                          PolicyParametrization parametrization, ServiceRepository serviceRepository,
                                          ClassLoaderRepository classLoaderRepository, List<ArtifactPlugin> artifactPlugins,
                                          ExtensionModelLoaderRepository extensionModelLoaderRepository) {
    this.application = application;
    this.template = template;
    this.parametrization = parametrization;
    this.serviceRepository = serviceRepository;
    this.classLoaderRepository = classLoaderRepository;
    this.artifactPlugins = artifactPlugins;
    this.extensionModelLoaderRepository = extensionModelLoaderRepository;
  }

  private void initPolicyContext() throws InitialisationException {
    ArtifactContextBuilder artifactBuilder =
        newBuilder().setArtifactType(APP)
            .setArtifactProperties(new HashMap<>(parametrization.getParameters()))
            .setArtifactName(parametrization.getId())
            .setConfigurationFiles(new String[] {parametrization.getConfig().getAbsolutePath()})
            .setExecutionClassloader(template.getArtifactClassLoader().getClassLoader())
            .setServiceRepository(serviceRepository)
            .setClassLoaderRepository(classLoaderRepository)
            .setArtifactPlugins(artifactPlugins)
            .setExtensionManagerFactory(new CompositeArtifactExtensionManagerFactory(application, extensionModelLoaderRepository,
                                                                                     artifactPlugins,
                                                                                     new DefaultExtensionManagerFactory()));

    artifactBuilder.withServiceConfigurator(customizationService -> {
      MuleRegistry applicationRegistry = application.getMuleContext().getRegistry();
      customizationService.overrideDefaultServiceImpl(OBJECT_LOCK_PROVIDER,
                                                      applicationRegistry.lookupObject(OBJECT_LOCK_PROVIDER));
      customizationService.overrideDefaultServiceImpl(OBJECT_STORE_MANAGER,
                                                      applicationRegistry.lookupObject(OBJECT_STORE_MANAGER));
      customizationService.overrideDefaultServiceImpl(BASE_PERSISTENT_OBJECT_STORE_KEY,
                                                      applicationRegistry.lookupObject(BASE_PERSISTENT_OBJECT_STORE_KEY));
      customizationService.overrideDefaultServiceImpl(BASE_IN_MEMORY_OBJECT_STORE_KEY,
                                                      applicationRegistry.lookupObject(BASE_IN_MEMORY_OBJECT_STORE_KEY));
      customizationService.overrideDefaultServiceImpl(OBJECT_POLICY_MANAGER_STATE_HANDLER,
                                                      applicationRegistry.lookupObject(OBJECT_POLICY_MANAGER_STATE_HANDLER));
      customizationService.overrideDefaultServiceImpl(OBJECT_TIME_SUPPLIER,
                                                      applicationRegistry.lookupObject(OBJECT_TIME_SUPPLIER));
      Object muleClusterManager = applicationRegistry.lookupObject("_muleClusterManager");
      if (muleClusterManager != null) {
        customizationService.registerCustomServiceImpl("_muleClusterManager", // TODO parameterize
                                                       muleClusterManager);// CLUSTER_MANAGER_ID));
      }
    });
    try {
      policyContext = artifactBuilder.build();
      policyContext.getMuleContext().start();
    } catch (MuleException e) {
      throw new InitialisationException(createStaticMessage("Cannot create artifact context for the policy instance"), e, this);
    }
  }

  private void initPolicyInstance() throws InitialisationException {
    try {
      policyInstance = policyContext.getMuleContext().getRegistry().lookupObject(DefaultPolicyInstance.class);
    } catch (RegistrationException e) {
      throw new InitialisationException(createStaticMessage(
                                                            String.format("More than one %s found on context",
                                                                          ApplicationPolicyInstance.class)),
                                        e, this);
    }
  }

  @Override
  public PolicyPointcut getPointcut() {
    return parametrization.getPointcut();
  }

  @Override
  public int getOrder() {
    return parametrization.getOrder();
  }

  @Override
  public void initialise() throws InitialisationException {
    if (policyInstance == null) {
      synchronized (this) {
        if (policyContext == null) {
          initPolicyContext();
        }
        initPolicyInstance();
      }
    }
  }

  @Override
  public void dispose() {
    if (policyContext != null) {
      policyContext.getMuleContext().dispose();
    }
  }

  @Override
  public Optional<Policy> getSourcePolicy() {
    if (policyInstance.getSourcePolicyChain().isPresent()) {
      return of(new Policy(policyInstance.getSourcePolicyChain().get(), parametrization.getId()));
    } else {
      return empty();
    }
  }

  @Override
  public Optional<Policy> getOperationPolicy() {
    if (policyInstance.getOperationPolicyChain().isPresent()) {
      return of(new Policy(policyInstance.getOperationPolicyChain().get(), parametrization.getId()));
    } else {
      return empty();
    }
  }

}
