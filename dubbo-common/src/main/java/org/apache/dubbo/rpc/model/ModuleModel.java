/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ModuleEnvironment;
import org.apache.dubbo.common.context.ModuleExt;
import org.apache.dubbo.common.deploy.ApplicationDeployer;
import org.apache.dubbo.common.deploy.DeployState;
import org.apache.dubbo.common.deploy.ModuleDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.config.context.ModuleConfigManager;

import java.util.HashMap;
import java.util.Set;

/**
 * Model of a service module
 */
public class ModuleModel extends ScopeModel {
    private static final Logger logger = LoggerFactory.getLogger(ModuleModel.class);

    public static final String NAME = "ModuleModel";

    /**
     *  推测: ApplicationModel, 子门面, 其中也封装了其他组件
     */
    private final ApplicationModel applicationModel;
    private ModuleEnvironment moduleEnvironment;
    /**
     *  关键组件: 服务仓储
     */
    private ModuleServiceRepository serviceRepository;
    private ModuleConfigManager moduleConfigManager;
    /**
     * Module Deployer
     */
    private ModuleDeployer deployer;

    public ModuleModel(ApplicationModel applicationModel) {
        this(applicationModel, false);
    }

    public ModuleModel(ApplicationModel applicationModel, boolean isInternal) {
        super(applicationModel, ExtensionScope.MODULE, isInternal);
        Assert.notNull(applicationModel, "ApplicationModel can not be null");
        this.applicationModel = applicationModel;
        applicationModel.addModule(this, isInternal);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(getDesc() + " is created");
        }

        initialize();
        Assert.notNull(getServiceRepository(), "ModuleServiceRepository can not be null");
        Assert.notNull(getConfigManager(), "ModuleConfigManager can not be null");
        Assert.assertTrue(getConfigManager().isInitialized(), "ModuleConfigManager can not be initialized");

        // notify application check state
        ApplicationDeployer applicationDeployer = applicationModel.getDeployer();
        if (applicationDeployer != null) {
            applicationDeployer.notifyModuleChanged(this, DeployState.PENDING);
        }
    }

    @Override
    protected void initialize() {
        super.initialize();
        this.serviceRepository = new ModuleServiceRepository(this);

        initModuleExt();

        ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader = this.getExtensionLoader(ScopeModelInitializer.class);
        Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
        for (ScopeModelInitializer initializer : initializers) {
            initializer.initializeModuleModel(this);
        }
    }

    private void initModuleExt() {
        Set<ModuleExt> exts = this.getExtensionLoader(ModuleExt.class).getSupportedExtensionInstances();
        for (ModuleExt ext : exts) {
            ext.initialize();
        }
    }

    @Override
    protected void onDestroy() {
        // 1. remove from applicationModel
        applicationModel.removeModule(this);

        // 2. set stopping
        if (deployer != null) {
            deployer.preDestroy();
        }

        // 3. release services
        if (deployer != null) {
            deployer.postDestroy();
        }

        // destroy other resources
        notifyDestroy();

        if (serviceRepository != null) {
            serviceRepository.destroy();
            serviceRepository = null;
        }

        if (moduleEnvironment != null) {
            moduleEnvironment.destroy();
            moduleEnvironment = null;
        }

        if (moduleConfigManager != null) {
            moduleConfigManager.destroy();
            moduleConfigManager = null;
        }

        // destroy application if none pub module
        applicationModel.tryDestroy();
    }

    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    public ModuleServiceRepository getServiceRepository() {
        return serviceRepository;
    }

    @Override
    public void addClassLoader(ClassLoader classLoader) {
        super.addClassLoader(classLoader);
        if (moduleEnvironment != null) {
            moduleEnvironment.refreshClassLoaders();
        }
    }

    @Override
    public ModuleEnvironment getModelEnvironment() {
        if (moduleEnvironment == null) {
            moduleEnvironment = (ModuleEnvironment) this.getExtensionLoader(ModuleExt.class)
                .getExtension(ModuleEnvironment.NAME);
        }
        return moduleEnvironment;
    }

    public ModuleConfigManager getConfigManager() {
        if (moduleConfigManager == null) {
            moduleConfigManager = (ModuleConfigManager) this.getExtensionLoader(ModuleExt.class)
                .getExtension(ModuleConfigManager.NAME);
        }
        return moduleConfigManager;
    }

    public ModuleDeployer getDeployer() {
        return deployer;
    }

    public void setDeployer(ModuleDeployer deployer) {
        this.deployer = deployer;
    }

    /**
     * for ut only
     */
    @Deprecated
    public void setModuleEnvironment(ModuleEnvironment moduleEnvironment) {
        this.moduleEnvironment = moduleEnvironment;
    }

    public ConsumerModel registerInternalConsumer(Class<?> internalService, URL url) {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setVersion(url.getVersion());
        serviceMetadata.setGroup(url.getGroup());
        serviceMetadata.setDefaultGroup(url.getGroup());
        serviceMetadata.setServiceInterfaceName(internalService.getName());
        serviceMetadata.setServiceType(internalService);
        String serviceKey = URL.buildKey(internalService.getName(), url.getGroup(), url.getVersion());
        serviceMetadata.setServiceKey(serviceKey);

        ConsumerModel consumerModel = new ConsumerModel(serviceMetadata.getServiceKey(), "jdk", serviceRepository.lookupService(serviceMetadata.getServiceInterfaceName()),
            this, serviceMetadata, new HashMap<>(0), ClassUtils.getClassLoader(internalService));

        logger.info("Dynamically registering consumer model " + serviceKey + " into model " + this.getDesc());
        serviceRepository.registerConsumer(consumerModel);
        return consumerModel;
    }
}
