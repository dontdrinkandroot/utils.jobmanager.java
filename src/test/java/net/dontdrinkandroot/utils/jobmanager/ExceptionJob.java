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

import net.dontdrinkandroot.utils.progressmonitor.ProgressMonitor;
import net.dontdrinkandroot.utils.progressmonitor.ProgressStatus;
import net.dontdrinkandroot.utils.progressmonitor.impl.SimpleProgressMonitor;


public class ExceptionJob extends AbstractJob<Void>
{

	private final ProgressMonitor monitor = new SimpleProgressMonitor();


	public ExceptionJob()
	{
		super("testjob.exception");
	}

	@Override
	protected Void doRun() throws Exception
	{
		throw new Exception("Fail");
	}

	@Override
	public ProgressStatus getProgressStatus()
	{
		return this.monitor.getProgressStatus();
	}

}
