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

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;


public class JobManagerTest {

	@Test
	// TODO: evaluate how to correctly do JUnit Tests using threads.
	public void test() {

		final JobManager jobManager = new JobManager(2);

		final Set<Thread> threads = new HashSet<Thread>();
		for (int i = 0; i < 10; i++) {
			final Thread thread = new Thread() {

				@Override
				public void run() {

					final Integer jobInteger = (int) (Math.random() * 10);
					AbstractJob<Integer> testJob = new SleepingJob(jobInteger);
					Integer result = null;
					int lastPosition = Integer.MAX_VALUE;

					do {

						try {
							Job<Integer> jobResult = jobManager.claimInterest(testJob, 1000L);
							int qPosition = jobManager.getQueuePosition(testJob);
							Assert.assertTrue(qPosition <= lastPosition);
							lastPosition = qPosition;

							if (!jobResult.isFinished()) {

								try {
									Thread.sleep(100L);
								} catch (final InterruptedException e) {
									throw new RuntimeException(e);
								}

							} else {

								result = jobResult.getResult();
								Assert.assertNotNull(result);

							}
						} catch (OvercapacityException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

					} while (result == null);

					Assert.assertEquals(new Integer(jobInteger * 10), result);
				};
			};
			thread.start();
			threads.add(thread);
		}

		for (final Thread t : threads) {
			try {
				t.join();
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}

		jobManager.stop();
	}


	@Test
	public void testSingleThreaded() throws OvercapacityException {

		final JobManager jobManager = new JobManager(1);

		try {

			String[] jobIds = new String[3];

			try {
				for (int i = 0; i < 3; i++) {
					jobIds[i] = jobManager.claimInterest(new SleepingJob(i), 1000L).getId();
				}
				jobIds[0] = jobManager.claimInterest(new SleepingJob(0), 1000L).getId();
			} catch (OvercapacityException e) {
				throw e;
			}

			for (int i = 0; i < 3; i++) {
				while (!jobManager.isJobFinished(jobIds[i])) {
					try {
						Thread.sleep(100L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				Assert.assertEquals(i * 10, jobManager.getResult(jobIds[i]));
			}

		} finally {
			jobManager.stop();
		}
	}


	@Test
	public void testException() throws OvercapacityException {

		final JobManager jobManager = new JobManager(1);
		AbstractJob<Void> job = new ExceptionJob();
		Job<Void> jobResult = jobManager.claimInterest(job, 1000L);
		while (!jobResult.isFinished()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Assert.assertTrue(jobResult.failed());
		Assert.assertNotNull(jobResult.getException());
		Assert.assertEquals("Fail", jobResult.getException().getMessage());
		jobManager.stop();
	}


	@Test
	public void testMaxQueuePosition() throws OvercapacityException {

		final JobManager jobManager = new JobManager(1);
		jobManager.setMaxQueueLength(10);

		try {
			jobManager.claimInterest(new SleepingJob(0), 1000L, 1);
		} catch (OvercapacityException e) {
			throw e;
		}

		try {
			jobManager.claimInterest(new SleepingJob(1), 1000L, 1);
			jobManager.claimInterest(new SleepingJob(2), 1000L, 1);
			jobManager.claimInterest(new SleepingJob(3), 1000L, 0);
			Assert.fail("Exception expected");
		} catch (OvercapacityException e) {
			/* Expected */
		}

		jobManager.stop();
	}


	@Test
	public void testOverCapacity() throws OvercapacityException {

		final JobManager jobManager = new JobManager(1);
		jobManager.setMaxQueueLength(1);

		try {
			jobManager.claimInterest(new SleepingJob(0), 1000L);
		} catch (OvercapacityException e) {
			throw e;
		}

		try {
			jobManager.claimInterest(new SleepingJob(1), 1000L);
			jobManager.claimInterest(new SleepingJob(2), 1000L);
			jobManager.claimInterest(new SleepingJob(3), 1000L);
			Assert.fail("Exception expected");
		} catch (OvercapacityException e) {
			/* Expected */
		}

		jobManager.stop();
	}
}
