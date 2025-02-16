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
package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.rpc.service.GenericService;

public class Application {
    public static void main(String[] args) {
        if (isClassic(args)) {
            runWithRefer();
        } else {
            runWithBootstrap();
        }
    }

    private static boolean isClassic(String[] args) {
        return args.length > 0 && "classic".equalsIgnoreCase(args[0]);
    }

    private static void runWithBootstrap() {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setInterface(DemoService.class);
        reference.setGeneric("true");

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
            .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
            .protocol(new ProtocolConfig(CommonConstants.DUBBO, -1))
            .reference(reference)
            .start();

        DemoService demoService = bootstrap.getCache().get(reference);
        String message = demoService.sayHello("dubbo");
        System.out.println(message);

        // generic invoke
        GenericService genericService = (GenericService) demoService;
        Object genericInvokeResult = genericService.$invoke("sayHello", new String[]{String.class.getName()},
            new Object[]{"dubbo generic invoke"});
        System.out.println(genericInvokeResult);
    }

    private static void runWithRefer() {
        // ReferenceConfig: 服务引用的配置信息, 通过泛型传递服务对外暴露的接口
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        // consumer本质上也属于一个服务, 进行应用服务相关配置
        reference.setApplication(new ApplicationConfig("dubbo-demo-api-consumer"));
        // 注册中心相关配置
        reference.setRegistry(new RegistryConfig("zookeeper://127.0.0.1:2181"));
        // 元数据上报相关配置
        reference.setMetadataReportConfig(new MetadataReportConfig("zookeeper://127.0.0.1:2181"));
        // 明确设置服务接口的实现
        reference.setInterface(DemoService.class);

        // 通过 ReferenceConfig 的 get 方法
        // 获取动态代理生成的服务接口, 对服务拉取、网络通信等细节进行代理
        // 调用该接口, 底层必然会去调用 provider 的服务接口
        DemoService service = reference.get();
        String message = service.sayHello("dubbo");
        System.out.println(message);
    }
}
