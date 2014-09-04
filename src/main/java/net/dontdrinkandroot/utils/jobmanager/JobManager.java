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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.dontdrinkandroot.utils.progressmonitor.ProgressStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JobManager
{

	public static final int QUEUE_LENGTH_UNLIMITED = -1;

	public static int DEFAULT_NUM_JOBS = 2;

	private static final int QUEUE_POSITION_FINISHED = -1;

	private static final int QUEUE_POSITION_RUNNING = 0;

	private int maxQueueLength = -1;

	private final int maxJobs;

	private final Set<JobRunner> jobRunners = new HashSet<JobRunner>();

	private final List<AbstractJob<?>> queuedJobs = new ArrayList<AbstractJob<?>>();

	private final Map<String, AbstractJob<?>> jobs = new HashMap<String, AbstractJob<?>>();

	private final Map<String, AbstractJob<?>> finishedJobs = new HashMap<String, AbstractJob<?>>();

	private final Map<String, AbstractJob<?>> runningJobs = new HashMap<String, AbstractJob<?>>();

	private final Logger logger = LoggerFactory.getLogger(this.getClass());


	/**
	 * Creates a new JobManager with DEFAULT_NUM_JOBS executors.
	 */
	public JobManager()
	{
		this(JobManager.DEFAULT_NUM_JOBS);
	}

	public void setMaxQueueLength(int maxQueueLength)
	{
		this.maxQueueLength = maxQueueLength;
	}

	public int getMaxQueueLength()
	{
		return this.maxQueueLength;
	}

	/**
	 * Creates a new JobManager with the given number of executors.
	 * 
	 * @param maxJobs
	 *            Maximum number of jobs to execute in parallel.
	 */
	public JobManager(final int maxJobs)
	{
		this.logger.info("Creating jobManager with {} executors", maxJobs);

		this.maxJobs = maxJobs;

		for (int i = 0; i < this.maxJobs; i++) {
			final JobRunner jobRunner = new JobRunner("jobRunner" + i, this);
			this.jobRunners.add(jobRunner);
			jobRunner.start();
		}
	}

	/**
	 * Claim interest in the given job for the given duration. If the job doesn't exist yet is is enqueued.
	 * 
	 * @param job
	 *            The job to claim interest in.
	 * @param duration
	 *            The maximum duration that we want the job to stay alive.
	 * @return An attached instance of the job.
	 * @throws OvercapacityException
	 *             Thrown if the job didn't exist yet and the job queue is full.
	 */
	public synchronized <T> Job<T> claimInterest(final AbstractJob<T> job, final long duration)
			throws OvercapacityException
	{
		return this.claimInterest(job, duration, JobManager.QUEUE_LENGTH_UNLIMITED);
	}

	/**
	 * Claim interest in the given job for the given duration. If the job doesn't exist yet is is enqueued.
	 * 
	 * @param job
	 *            The job to claim interest in.
	 * @param duration
	 *            The maximum duration that we want the job to stay alive.
	 * @param maxQueuePosition
	 *            Only enqueue job if the job's queue position would not be greater than the given position.
	 * @return An attached instance of the job.
	 * @throws OvercapacityException
	 *             Thrown if the job didn't exist yet and the job queue is full.
	 */
	public synchronized <T> Job<T> claimInterest(final AbstractJob<T> job, final long duration, int maxQueuePosition)
			throws OvercapacityException
	{
		String id = job.getId();

		@SuppressWarnings("unchecked")
		AbstractJob<T> existingJob = (AbstractJob<T>) this.jobs.get(id);

		if (existingJob == null) {
			boolean queueLimited = this.maxQueueLength != JobManager.QUEUE_LENGTH_UNLIMITED;
			boolean queueExceeded = this.queuedJobs.size() == this.maxQueueLength;
			boolean queueToLarge =
					maxQueuePosition != JobManager.QUEUE_LENGTH_UNLIMITED && this.queuedJobs.size() > maxQueuePosition;
			if (queueLimited && queueExceeded || queueToLarge) {
				throw new OvercapacityException("Queue is full");
			}
			this.queuedJobs.add(job);
			this.jobs.put(id, job);
			existingJob = job;
			this.logger.info("Adding job with id " + job.getId());
		}

		long expiry = System.currentTimeMillis() + duration;
		existingJob.setExpiry(expiry);

		this.logger.debug("Claiming interest in job with id " + id + ", new timeout: " + expiry);

		if (!this.queuedJobs.isEmpty()) {
			this.triggerIdle();
		}

		return existingJob;
	}

	/**
	 * Claims interest in the job with the given id for the given duration.
	 * 
	 * @param jobId
	 *            The jobId.
	 * @param duration
	 *            The duration for which one is interested in the job.
	 * @return Returns the job if it exists or null.
	 */
	public synchronized Job<?> claimInterest(final String jobId, final long duration)
	{
		AbstractJob<?> existingJob = this.jobs.get(jobId);

		if (existingJob == null) {
			this.logger.warn("Claiming interest for job with id" + jobId + " failed: Not found");
			return null;
		}

		long expiry = System.currentTimeMillis() + duration;
		existingJob.setExpiry(expiry);

		this.logger.debug("Claiming interest in job with id " + jobId + ", new timeout: " + expiry);

		this.cleanUp();

		if (!this.queuedJobs.isEmpty()) {
			this.triggerIdle();
		}

		return existingJob;
	}

	/**
	 * Returns the progress status for the job with the given id.
	 * 
	 * @param jobId
	 *            The job id to get the progress status for.
	 * @return The progress status of the job or null if it was not found.
	 */
	public ProgressStatus getProgressStatus(String jobId)
	{
		if (this.finishedJobs.containsKey(jobId)) {
			return new ProgressStatus(100, "Done");
		}

		if (this.runningJobs.containsKey(jobId)) {
			AbstractJob<?> job = this.runningJobs.get(jobId);
			return job.getProgressStatus();
		}

		int position = 1;
		for (AbstractJob<?> job : this.queuedJobs) {
			if (jobId.equals(job.getId())) {
				return new ProgressStatus(0, "Number " + position + " in queue");
			}
			position++;
		}

		return null;
	}

	/**
	 * Gets the queue position for the job with the given id.
	 * 
	 * @param jobId
	 *            The job to get the queue position for.
	 * @return Returns the queue position (-1 for finished and 0 for running jobs) or null if the job was not found.
	 */
	public synchronized Integer getQueuePosition(String jobId)
	{
		if (this.finishedJobs.containsKey(jobId)) {
			return JobManager.QUEUE_POSITION_FINISHED;
		}

		if (this.runningJobs.containsKey(jobId)) {
			return JobManager.QUEUE_POSITION_RUNNING;
		}

		int position = 1;
		for (AbstractJob<?> job : this.queuedJobs) {
			if (jobId.equals(job.getId())) {
				return position;
			}
			position++;
		}

		return null;
	}

	/**
	 * Gets the queue position for the given job.
	 * 
	 * @param job
	 *            The job to get the queue position for.
	 * @return Returns the queue position (-1 for finished and 0 for running jobs) or null if the job was not found.
	 */
	public synchronized Integer getQueuePosition(AbstractJob<?> job)
	{
		return this.getQueuePosition(job.getId());
	}

	/**
	 * Get number of finished jobs that can be retrieved.
	 */
	public int getNumFinished()
	{
		return this.finishedJobs.size();
	}

	/**
	 * Get number of queued jobs.
	 */
	public int getNumQueued()
	{
		return this.queuedJobs.size();
	}

	/**
	 * Stops the jobManager, <b>MUST</b> be called or the runner threads are not shutdown.
	 */
	public void stop()
	{
		for (final JobRunner runner : this.jobRunners) {
			runner.stopRunner();
		}
	}

	/**
	 * Returns a Set of the currently processed jobs.
	 */
	public synchronized Set<Job<?>> getRunningJobs()
	{

		Set<Job<?>> runningJobs = new HashSet<Job<?>>();
		for (JobRunner jobRunner : this.jobRunners) {
			if (jobRunner.getCurrentJob() != null) {
				runningJobs.add(jobRunner.getCurrentJob());
			}
		}

		return runningJobs;
	}

	/**
	 * Returns a List of the currently queued jobs.
	 */
	public synchronized List<Job<?>> getQueuedJobs()
	{
		List<Job<?>> jobs = new ArrayList<Job<?>>(this.queuedJobs.size());
		for (Job<?> queuedJob : this.queuedJobs) {
			jobs.add(queuedJob);
		}

		return jobs;
	}

	private synchronized void cleanUp()
	{
		final Iterator<Entry<String, AbstractJob<?>>> jobIterator = this.jobs.entrySet().iterator();
		while (jobIterator.hasNext()) {

			Entry<String, AbstractJob<?>> current = jobIterator.next();
			String jobId = current.getKey();
			AbstractJob<?> job = current.getValue();

			if (job.getExpiry().isExpired()) {

				if (this.finishedJobs.containsKey(jobId)) {

					if (job.failed()) {
						this.logger.info("Removing failed job with id " + jobId + ": " + job.getException());
					} else {
						this.logger.info("Removing finished job with id " + jobId);
					}
					this.finishedJobs.remove(jobId);
					jobIterator.remove();

				} else {

					int idx = -1;
					int pos = 0;
					for (AbstractJob<?> queuedJob : this.queuedJobs) {
						if (jobId.equals(queuedJob.getId())) {
							idx = pos;
							break;
						}
						pos++;
					}

					if (idx != -1) {

						this.logger.info("Removing queued job with id " + jobId);
						this.queuedJobs.remove(idx);
						jobIterator.remove();

					} else if (!this.runningJobs.containsKey(job.getId())) {

						jobIterator.remove();

					}

				}

			}

		}
	}

	private synchronized void triggerIdle()
	{
		for (final JobRunner runner : this.jobRunners) {
			if (runner.isIdle()) {
				runner.wakeUp();
				return;
			}
		}
	}

	public int getNumRunning()
	{
		return this.runningJobs.size();
	}

	synchronized void jobDone(final AbstractJob<?> job)
	{
		this.logger.info("Job with id " + job.getId() + " done");

		this.runningJobs.remove(job.getId());
		this.finishedJobs.put(job.getId(), job);
	}

	synchronized AbstractJob<?> popNextJob()
	{
		this.cleanUp();

		if (this.queuedJobs.isEmpty()) {
			return null;
		}

		final AbstractJob<?> job = this.queuedJobs.get(0);
		this.queuedJobs.remove(0);

		this.runningJobs.put(job.getId(), job);

		return job;
	}

	/**
	 * Get the job with the given id or null if not found.
	 * 
	 * @param jobId
	 *            The id of the job to find.
	 * @return The job if found or null.
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> Job<T> getJob(String jobId)
	{
		return (Job<T>) this.jobs.get(jobId);

	}

	public synchronized boolean isJobFinished(String jobId)
	{
		return this.finishedJobs.containsKey(jobId);
	}

	@SuppressWarnings("unchecked")
	public synchronized <T> T getResult(String jobId)
	{
		if (!this.jobs.containsKey(jobId)) {
			return null;
		}

		return (T) this.jobs.get(jobId).getResult();
	}

}
