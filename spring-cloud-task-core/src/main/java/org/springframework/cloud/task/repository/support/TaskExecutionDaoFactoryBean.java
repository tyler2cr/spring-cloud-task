/*
 * Copyright 2015-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.task.repository.support;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.batch.item.database.support.DataFieldMaxValueIncrementerFactory;
import org.springframework.batch.item.database.support.DefaultDataFieldMaxValueIncrementerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cloud.task.configuration.TaskProperties;
import org.springframework.cloud.task.listener.TaskException;
import org.springframework.cloud.task.repository.dao.JdbcTaskExecutionDao;
import org.springframework.cloud.task.repository.dao.MapTaskExecutionDao;
import org.springframework.cloud.task.repository.dao.TaskExecutionDao;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link FactoryBean} implementation that creates the appropriate
 * {@link TaskExecutionDao} based on the provided information.
 *
 * @author Michael Minella
 * @author Glenn Renfro
 */
public class TaskExecutionDaoFactoryBean implements FactoryBean<TaskExecutionDao> {

	private DataSource dataSource;

	private TaskExecutionDao dao = null;

	private String tablePrefix = TaskProperties.DEFAULT_TABLE_PREFIX;

	/**
	 * Default constructor will result in a Map based TaskExecutionDao. <b>This is only
	 * intended for testing purposes.</b>
	 */
	public TaskExecutionDaoFactoryBean() {
	}

	/**
	 * {@link DataSource} to be used.
	 * @param dataSource {@link DataSource} to be used.
	 * @param tablePrefix the table prefix to use for this dao.
	 */
	public TaskExecutionDaoFactoryBean(DataSource dataSource, String tablePrefix) {
		this(dataSource);
		Assert.hasText(tablePrefix, "tablePrefix must not be null nor empty");
		this.tablePrefix = tablePrefix;
	}

	/**
	 * {@link DataSource} to be used.
	 * @param dataSource {@link DataSource} to be used.
	 */
	public TaskExecutionDaoFactoryBean(DataSource dataSource) {
		Assert.notNull(dataSource, "A DataSource is required");

		this.dataSource = dataSource;
	}

	@Override
	public TaskExecutionDao getObject() throws Exception {
		if (this.dao == null) {
			if (this.dataSource != null) {
				buildTaskExecutionDao(this.dataSource);
			}
			else {
				this.dao = new MapTaskExecutionDao();
			}
		}
		if (this.dataSource != null) {
			String databaseType = null;
			try {
				databaseType = DatabaseType.fromMetaData(dataSource).name();
			}
			catch (MetaDataAccessException e) {
				throw new IllegalStateException(e);
			}
			if (StringUtils.hasText(databaseType) && databaseType.equals("SQLSERVER")) {
				String incrementerName = this.tablePrefix + "SEQ";
				DataFieldMaxValueIncrementerFactory incrementerFactory = new DefaultDataFieldMaxValueIncrementerFactory(
						dataSource);
				DataFieldMaxValueIncrementer incrementer = incrementerFactory
						.getIncrementer(databaseType, incrementerName);
				if (!isSqlServerTableSequenceAvailable(incrementerName)) {
					incrementer = new SqlServerSequenceMaxValueIncrementer(dataSource, this.tablePrefix + "SEQ");
				}
				((JdbcTaskExecutionDao) this.dao).setTaskIncrementer(incrementer);
			}
		}
		return this.dao;
	}

	@Override
	public Class<?> getObjectType() {
		return TaskExecutionDao.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	private void buildTaskExecutionDao(DataSource dataSource) {
		DataFieldMaxValueIncrementerFactory incrementerFactory = new DefaultDataFieldMaxValueIncrementerFactory(
				dataSource);
		this.dao = new JdbcTaskExecutionDao(dataSource, this.tablePrefix);
		String databaseType;
		try {
			databaseType = DatabaseType.fromMetaData(dataSource).name();
		}
		catch (MetaDataAccessException e) {
			throw new IllegalStateException(e);
		}
		catch (SQLException e) {
			throw new IllegalStateException(e);
		}
		((JdbcTaskExecutionDao) this.dao).setTaskIncrementer(incrementerFactory
			.getIncrementer(databaseType, this.tablePrefix + "SEQ"));
	}

	private boolean isSqlServerTableSequenceAvailable(String incrementerName) {
		boolean result = false;
		DatabaseMetaData metaData = null;
		try {
			metaData = dataSource.getConnection().getMetaData();
			String[] types = { "TABLE" };
			ResultSet tables = metaData.getTables(null, null, "%", types);
			while (tables.next()) {
				if (tables.getString("TABLE_NAME").equals(incrementerName)) {
					result = true;
					break;
				}
			}
		}
		catch (SQLException sqe) {
			throw new TaskException(sqe.getMessage());
		}
		return result;
	}

}
