package org.fakekoji.jobmanager;

import org.fakekoji.Utils;
import org.fakekoji.core.AccessibleSettings;
import org.fakekoji.jobmanager.model.JDKProject;
import org.fakekoji.jobmanager.model.JDKTestProject;
import org.fakekoji.jobmanager.model.Job;
import org.fakekoji.jobmanager.model.JobUpdateResult;
import org.fakekoji.jobmanager.model.JobUpdateResults;
import org.fakekoji.jobmanager.model.Project;
import org.fakekoji.jobmanager.model.TaskJob;
import org.fakekoji.jobmanager.project.JDKProjectParser;
import org.fakekoji.model.Platform;
import org.fakekoji.model.Task;
import org.fakekoji.storage.StorageException;
import org.fakekoji.xmlrpc.server.JavaServerConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JenkinsJobUpdater implements JobUpdater {

    private static final Logger LOGGER = Logger.getLogger(JavaServerConstants.FAKE_KOJI_LOGGER);
    static final String JENKINS_JOB_CONFIG_FILE = "config.xml";

    private final AccessibleSettings settings;

    public JenkinsJobUpdater(AccessibleSettings settings) {
        this.settings = settings;
    }

    @Override
    public JobUpdateResults update(Project oldProject, Project newProject) throws StorageException, ManagementException {

        final JDKProjectParser jdkProjectParser = new JDKProjectParser(
                ConfigManager.create(settings.getConfigRoot().getAbsolutePath()),
                settings.getLocalReposRoot(),
                settings.getScriptsRoot()
        );
        final Set<Job> oldJobs;
        final Set<Job> newJobs;
        oldJobs = oldProject == null ? Collections.emptySet() : jdkProjectParser.parse(oldProject);
        newJobs = newProject == null ? Collections.emptySet() : jdkProjectParser.parse(newProject);

        return update(oldJobs, newJobs);
    }

    public JobUpdateResults update(Platform platform) throws StorageException {
        final Predicate<Job> platformJobPredicate = job ->
                job instanceof TaskJob && ((TaskJob) job).getPlatform().getId().equals(platform.getId());
        final List<JobUpdateResult> jobsRewritten = update(platformJobPredicate, jobUpdateFunctionWrapper(getRewriteFunction()));
        return new JobUpdateResults(
                Collections.emptyList(),
                Collections.emptyList(),
                jobsRewritten,
                Collections.emptyList()
        );
    }

    public JobUpdateResults update(Task task) throws StorageException {
        final Predicate<Job> taskJobPredicate = job ->
                job instanceof TaskJob && ((TaskJob) job).getTask().getId().equals(task.getId());
        final List<JobUpdateResult> jobsRewritten = update(taskJobPredicate, jobUpdateFunctionWrapper(getRewriteFunction()));
        return new JobUpdateResults(
                Collections.emptyList(),
                Collections.emptyList(),
                jobsRewritten,
                Collections.emptyList()
        );
    }

    private List<JobUpdateResult> update(
            final Predicate<Job> jobPredicate,
            final Function<Job, JobUpdateResult> jobUpdateFunction
    ) throws StorageException {

        final ConfigManager configManager = ConfigManager.create(settings.getConfigRoot().getAbsolutePath());
        final JDKProjectParser jdkProjectParser = new JDKProjectParser(
                configManager,
                settings.getLocalReposRoot(),
                settings.getScriptsRoot()
        );
        final Set<Job> jobs = Stream.of(
                configManager
                        .getJdkProjectStorage()
                        .loadAll(JDKProject.class),
                configManager
                        .getJdkTestProjectStorage()
                        .loadAll(JDKTestProject.class)
        )
                .flatMap(Collection::stream)
                .map(jdkProject -> {
                    try {
                        return jdkProjectParser.parse(jdkProject);
                    } catch (ManagementException | StorageException e) {
                        LOGGER.severe("Failed to parse JDK project " + jdkProject.getId() + ": " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        return jobs.stream()
                .filter(jobPredicate)
                .map(jobUpdateFunction)
                .collect(Collectors.toList());
    }

    JobUpdateResults update(Set<Job> oldJobs, Set<Job> newJobs) {

        final Function<Job, JobUpdateResult> rewriteFunction = jobUpdateFunctionWrapper(getRewriteFunction());
        final Function<Job, JobUpdateResult> createFunction = jobUpdateFunctionWrapper(getCreateFunction());
        final Function<Job, JobUpdateResult> archiveFunction = jobUpdateFunctionWrapper(getArchiveFunction());
        final Function<Job, JobUpdateResult> reviveFunction = jobUpdateFunctionWrapper(getReviveFunction());

        final List<JobUpdateResult> jobsCreated = new LinkedList<>();
        final List<JobUpdateResult> jobsArchived = new LinkedList<>();
        final List<JobUpdateResult> jobsRewritten = new LinkedList<>();
        final List<JobUpdateResult> jobsRevived = new LinkedList<>();

        final Set<String> archivedJobs = new HashSet<>(Arrays.asList(Objects.requireNonNull(settings.getJenkinsJobArchiveRoot().list())));

        for (final Job job : oldJobs) {
            if (newJobs.stream().noneMatch(newJob -> job.toString().equals(newJob.toString()))) {
                jobsArchived.add(archiveFunction.apply(job));
            }
        }
        for (final Job job : newJobs) {
            if (archivedJobs.contains(job.toString())) {
                jobsRevived.add(reviveFunction.apply(job));
                continue;
            }
            final Optional<Job> optional = oldJobs.stream()
                    .filter(oldJob -> job.toString().equals(oldJob.toString()))
                    .findAny();
            if (optional.isPresent()) {
                final Job oldJob = optional.get();
                if (!oldJob.equals(job)) {
                    jobsRewritten.add(rewriteFunction.apply(job));
                }
                continue;
            }
            jobsCreated.add(createFunction.apply(job));
        }
        return new JobUpdateResults(
                jobsCreated,
                jobsArchived,
                jobsRewritten,
                jobsRevived
        );
    }

    private Function<Job, JobUpdateResult> jobUpdateFunctionWrapper(JobUpdateFunction updateFunction) {
        return job -> {
            try {
                return updateFunction.apply(job);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return new JobUpdateResult(job.toString(), false, e.getMessage());
            }
        };
    }

    private JobUpdateFunction getCreateFunction() {
        return job -> {
            final String jobName = job.toString();
            LOGGER.info("Creating job " + jobName);
            final String jobsRootPath = settings.getJenkinsJobsRoot().getAbsolutePath();
            LOGGER.info("Creating directory " + jobName + " in " + jobsRootPath);
            final File jobDir = Paths.get(jobsRootPath, jobName).toFile();
            if (!jobDir.mkdir()) {
                throw new IOException("Could't create file: " + jobDir.getAbsolutePath());
            }
            final String jobDirPath = jobDir.getAbsolutePath();
            LOGGER.info("Creating file " + JENKINS_JOB_CONFIG_FILE + " in " + jobDirPath);
            return new PrimaryExceptionThrower<JobUpdateResult>(
                    () -> {
                        Utils.writeToFile(
                                Paths.get(jobDirPath, JENKINS_JOB_CONFIG_FILE),
                                job.generateTemplate()
                        );
                    }, () -> {
                createManuallyUploadedJob(jobName);
            }, new JobUpdateResult(jobName, true)).call();
        };
    }

    private JobUpdateFunction getReviveFunction() {
        return job -> {
            final String jobName = job.toString();
            final File src = Paths.get(settings.getJenkinsJobArchiveRoot().getAbsolutePath(), job.toString()).toFile();
            final File dst = Paths.get(settings.getJenkinsJobsRoot().getAbsolutePath(), job.toString()).toFile();
            LOGGER.info("Reviving job " + jobName);
            LOGGER.info("Moving directory " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
            return new PrimaryExceptionThrower<JobUpdateResult>(
                    () -> {
                        Utils.moveDir(src, dst);
                        //regenerate conig
                        LOGGER.info("recreating file " + JENKINS_JOB_CONFIG_FILE + " in " + dst);
                        Utils.writeToFile(
                                Paths.get(dst.getAbsolutePath(), JENKINS_JOB_CONFIG_FILE),
                                job.generateTemplate());
                    }, () -> {
                createManuallyUploadedJob(jobName);
            }, new JobUpdateResult(jobName, true)).call();
        };
    }

    private JobUpdateFunction getArchiveFunction() {
        return job -> {
            final String jobName = job.toString();
            final File src = Paths.get(settings.getJenkinsJobsRoot().getAbsolutePath(), job.toString()).toFile();
            final File dst = Paths.get(settings.getJenkinsJobArchiveRoot().getAbsolutePath(), job.toString()).toFile();
            LOGGER.info("Archiving job " + jobName);
            LOGGER.info("Moving directory " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
            Utils.moveDir(src, dst);
            //we delte only if archivation suceed
            JenkinsCliWrapper.getCli().deleteJobs(jobName).throwIfNecessary();
            return new JobUpdateResult(jobName, true);
        };
    }

    private JobUpdateFunction getRewriteFunction() {
        return job -> {
            final String jobName = job.toString();
            final File jobConfig = Paths.get(settings.getJenkinsJobsRoot().getAbsolutePath(), jobName, JENKINS_JOB_CONFIG_FILE).toFile();
            LOGGER.info("Rewriting job " + jobName);
            LOGGER.info("Writing to file " + jobConfig.getAbsolutePath());
            return new PrimaryExceptionThrower<JobUpdateResult>(
                    () -> {
                        Utils.writeToFile(jobConfig, job.generateTemplate());
                    }, () -> {
                updateManuallyUpdatedJob(jobName);
            }, new JobUpdateResult(jobName, true)).call();
        };
    }

    private void createManuallyUploadedJob(final String jobName) throws Exception {
        JenkinsCliWrapper.getCli().createManuallyUploadedJob(settings.getJenkinsJobsRoot(), jobName).throwIfNecessary();
        ;
    }

    private void updateManuallyUpdatedJob(final String jobName) throws Exception {
        JenkinsCliWrapper.getCli().updateManuallyUpdatedJob(settings.getJenkinsJobsRoot(), jobName).throwIfNecessary();
        ;
    }

    private static interface JobUpdateFunction {

        JobUpdateResult apply(Job job) throws Exception;
    }

    static interface Rummable {

        public void rum() throws Exception;
    }

    static class PrimaryExceptionThrower<T> {

        private final Rummable mainCall;
        private final Rummable finalCall;
        private final T result;

        public PrimaryExceptionThrower(Rummable mainCall, Rummable finalCall, T result) {
            this.mainCall = mainCall;
            this.finalCall = finalCall;
            this.result = result;
        }

        public synchronized T call() throws Exception {
            Exception saved = null;
            try {
                mainCall.rum();
            } catch (Exception mainEx) {
                saved = mainEx;
            } finally {
                try {
                    finalCall.rum();
                } catch (Exception secondary) {
                    if (saved != null) {
                        LOGGER.log(Level.SEVERE, secondary.getMessage(), secondary);
                        throw saved;
                    } else {
                        throw secondary;
                    }
                }
            }
            if (saved != null) {
                throw saved;
            }
            return result;
        }
    }
}
