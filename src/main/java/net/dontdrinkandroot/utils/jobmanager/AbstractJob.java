/**
 * Copyright (C) 2013 Philip W. Sorst <philip@sorst.net>
 * and individual contributors as indicated
 * by the @authors tag.
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
package net.dontdrinkandroot.utils.jobmanager;

import net.dontdrinkandroot.utils.progressmonitor.ProgressStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractJob<T> implements Job<T>
{

	protected T result;

	private Exception exception;

	private final String id;

	private final Expiry expiry;

	private boolean finished = false;

	private boolean failed = false;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());


	public AbstractJob(String id)
	{
		this.id = id;
		this.expiry = new Expiry();
	}

	@Override
	public boolean isFinished()
	{
		return this.finished;
	}

	@Override
	public T getResult()
	{
		return this.result;
	}

	@Override
	public Expiry getExpiry()
	{
		return this.expiry;
	}

	@Override
	public String getId()
	{

		return this.id;
	}

	@Override
	public Exception getException()
	{
		return this.exception;
	}

	@Override
	public boolean failed()
	{
		return this.failed;
	}

	public void run()
	{
		try {

			this.result = this.doRun();

		} catch (Exception e) {

			this.exception = e;
			this.failed = true;

		} finally {

			this.finished = true;

		}
	}

	public void setExpiry(long expiry)
	{
		this.expiry.setExpiry(expiry);
	}

	public Logger getLogger()
	{
		return this.logger;
	}

	public void setException(Exception exception)
	{
		this.exception = exception;
	}

	protected abstract T doRun() throws Exception;

	protected abstract ProgressStatus getProgressStatus();
}
