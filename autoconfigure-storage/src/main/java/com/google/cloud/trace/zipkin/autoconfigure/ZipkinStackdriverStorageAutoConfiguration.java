/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin.autoconfigure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.trace.grpc.v1.GrpcTraceSink;
import com.google.cloud.trace.v1.sink.TraceSink;
import com.google.cloud.trace.zipkin.StackdriverStorageComponent;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.storage.StorageComponent;

/**
 * Auto-configuration to set the Stackdriver StorageComponent as the Zipkin storage backend.
 */
@Configuration
@EnableConfigurationProperties(ZipkinStackdriverStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "stackdriver")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinStackdriverStorageAutoConfiguration {
  @Autowired
  ZipkinStackdriverStorageProperties storageProperties;

  @Bean
  @ConditionalOnMissingBean(Executor.class) Executor executor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinStackdriverStorage-");
    executor.initialize();
    return executor;
  }

  @Bean
  @ConditionalOnMissingBean(TraceSink.class)
  TraceSink traceSink() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/trace.append"));
    return new GrpcTraceSink(storageProperties.getApiHost(), credentials);
  }

  @Bean(name="projectId")
  String projectId() {
    String configuredProject = storageProperties.getProjectId();
    if (configuredProject != null && !configuredProject.isEmpty()) {
      return configuredProject;
    }
    try {
      return getDefaultProjectId();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Missing required property: projectId");
    }
  }

  String getDefaultProjectId() throws IOException {
    URL url = new URL("http://metadata.google.internal/computeMetadata/v1/project/project-id");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestProperty("Metadata-Flavor", "Google");
    connection.setRequestMethod("GET");
    InputStream responseStream = connection.getInputStream();
    String projectId = new String(ByteStreams.toByteArray(responseStream));
    return projectId;
  }

  @Bean StorageComponent storage(Executor executor, TraceSink sink, @Qualifier("projectId")
      String projectId) {
    return new StackdriverStorageComponent(projectId, sink, executor);
  }
}
