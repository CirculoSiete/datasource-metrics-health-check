/**
 *
 * Copyright (C) 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.circulosiete.metrics.health.hystrix;

import com.codahale.metrics.health.HealthCheck;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.codahale.metrics.health.HealthCheck.Result.healthy;
import static com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang.exception.ExceptionUtils.getCause;

/**
 * A Hystrix command to validate the health in a DataSource.
 *
 * @author Domingo Suarez Torres <domingo.suarez@gmail.com> (@domix)
 * @since 1/19/2017
 */
@Slf4j
public class TestQueryExecutorCommand extends HystrixCommand<HealthCheck.Result> {

  private final String dataSourceId;
  private final DataSource dataSource;
  private final String validationQuery;
  private final Integer timeout;

  /**
   * Create a new instance with the required values.
   *
   * @param dataSourceId
   * @param dataSource
   * @param validationQuery
   * @param groupKey
   */
  public TestQueryExecutorCommand(String dataSourceId, DataSource dataSource, String validationQuery, String groupKey) {
    this(dataSourceId, dataSource, validationQuery, groupKey, 300);
  }

  /**
   * Create a new instance with the required values.
   *
   * @param dataSourceId    The unique Id of the desired DataSource, useful for troubleshooting.
   * @param dataSource      The JDBC DataSource under test.
   * @param validationQuery The query to perform to validate the DataSource Health.
   * @param groupKey        The groupKey for Hystrix.
   * @param timeoutInMilliseconds         Timeout to wait before blah blah
   */
  public TestQueryExecutorCommand(String dataSourceId, DataSource dataSource, String validationQuery, String groupKey, Integer timeoutInMilliseconds) {
    super(Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
      .andCommandKey(HystrixCommandKey.Factory.asKey("TestQueryExecutorCommand"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionIsolationStrategy(SEMAPHORE)
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)));

    this.dataSourceId = dataSourceId;
    this.dataSource = dataSource;
    this.validationQuery = validationQuery;
    this.timeout = timeoutInMilliseconds;
  }

  @Override
  protected HealthCheck.Result run() throws Exception {
    log.debug("Running the HealthCheck");

    try (
      Connection con = dataSource.getConnection();
      PreparedStatement pstmt = con.prepareStatement(validationQuery)) {
      pstmt.executeQuery();
      log.debug(String.format("DB '%s' is healthy", dataSourceId));
    }
    return healthy(String.format("DB '%s' is OK", dataSourceId));
  }

  @Override
  protected HealthCheck.Result getFallback() {
    Throwable cause = getCause(getExecutionException());
    log.error(String.format("DB '%s' is unhealthy", dataSourceId), cause);

    if (isResponseTimedOut()) {
      log.warn("Timeout detectado: {}, timeout configurado: {}", getExecutionTimeInMilliseconds(), timeout);
    }

    return ofNullable(cause)
      .map(HealthCheck.Result::unhealthy)
      .orElse(HealthCheck.Result.unhealthy("Fail"));
  }
}
