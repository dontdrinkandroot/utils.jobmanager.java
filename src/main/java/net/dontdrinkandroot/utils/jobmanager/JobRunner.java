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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JobRunner extends Thread {

	private AbstractJob<?> currentJob = null;

	private final JobManager manager;

	private boolean stop = false;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());


	public JobRunner(String name, final JobManager manager) {

		super(name);
		this.manager = manager;
	}


	public JobManager getManager() {

		return this.manager;
	}


	@Override
	public void run() {

		do {
			this.currentJob = this.manager.popNextJob();
			if (this.currentJob == null) {

				try {
					Thread.sleep(10000L);
				} catch (final InterruptedException e) {
				}

			} else {

				this.logger.info("Running job with id " + this.currentJob.getId());
				this.currentJob.run();
				this.manager.jobDone(this.currentJob);

			}

		} while (!this.stop);
	}


	public AbstractJob<?> getCurrentJob() {

		return this.currentJob;
	}


	public boolean isIdle() {

		return this.currentJob == null;
	}


	public void stopRunner() {

		this.stop = true;
		this.interrupt();
	}


	public void wakeUp() {

		this.interrupt();
	}

}