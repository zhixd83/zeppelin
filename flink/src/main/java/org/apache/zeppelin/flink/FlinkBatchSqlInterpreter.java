/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink;

import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.io.IOException;
import java.util.Properties;

public class FlinkBatchSqlInterpreter extends FlinkSqlInterrpeter {

  private FlinkZeppelinContext z;

  public FlinkBatchSqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  protected boolean isBatch() {
    return true;
  }

  @Override
  public void open() throws InterpreterException {
    super.open();
    this.tbenv = flinkInterpreter.getJavaBatchTableEnvironment("blink");
    this.tbenv_2 = flinkInterpreter.getJavaBatchTableEnvironment("flink");
    this.z = flinkInterpreter.getZeppelinContext();
  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  public void callInnerSelect(String sql, InterpreterContext context) throws IOException {
    int defaultSqlParallelism = this.tbenv.getConfig().getConfiguration()
            .getInteger(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM);
    try {
      if (context.getLocalProperties().containsKey("parallelism")) {
        this.tbenv.getConfig().getConfiguration()
                .set(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM,
                        Integer.parseInt(context.getLocalProperties().get("parallelism")));
      }
      Table table = this.tbenv.sqlQuery(sql);
      z.setCurrentSql(sql);
      String result = z.showData(table);
      context.out.write(result);
    } finally {
      this.tbenv.getConfig().getConfiguration()
              .set(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM,
                      defaultSqlParallelism);
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    flinkInterpreter.getJobManager().cancelJob(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return flinkInterpreter.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = Integer.parseInt(properties.getProperty(
            "zeppelin.flink.concurrentBatchSql.max", "10"));
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
            FlinkBatchSqlInterpreter.class.getName(), maxConcurrency);
  }
}
