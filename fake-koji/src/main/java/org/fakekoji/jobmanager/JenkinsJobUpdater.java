package org.fakekoji.jobmanager;

import org.fakekoji.Utils;
import org.fakekoji.jobmanager.model.Job;
import org.fakekoji.jobmanager.model.JobUpdateResult;
import org.fakekoji.jobmanager.model.JobUpdateResults;
import org.fakekoji.xmlrpc.server.JavaServerConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

public class JenkinsJobUpdater implements JobUpdater {

    private static final Logger LOGGER = Logger.getLogger(JavaServerConstants.FAKE_KOJI_LOGGER);
    static final String JENKINS_JOB_CONFIG_FILE = "config.xml";

    private final File jobsRoot;
    private final File jobArchiveRoot;

    public JenkinsJobUpdater(File jobsRoot, File jobArchiveRoot) {
        this.jobsRoot = jobsRoot;
        this.jobArchiveRoot = jobArchiveRoot;
    }

    @Override
    public JobUpdateResults update(Set<Job> oldJobs, Set<Job> newJobs) {

        final Function<Job, JobUpdateResult> rewriteFunction = jobUpdateFunctionWrapper(getRewriteFunction());
        final Function<Job, JobUpdateResult> createFunction = jobUpdateFunctionWrapper(getCreateFunction());
        final Function<Job, JobUpdateResult> archiveFunction = jobUpdateFunctionWrapper(getArchiveFunction());
        final Function<Job, JobUpdateResult> reviveFunction = jobUpdateFunctionWrapper(getReviveFunction());

        final List<JobUpdateResult> jobsCreated = new LinkedList<>();
        final List<JobUpdateResult> jobsArchived = new LinkedList<>();
        final List<JobUpdateResult> jobsRewritten = new LinkedList<>();
        final List<JobUpdateResult> jobsRevived = new LinkedList<>();

        final Set<String> archivedJobs = new HashSet<>(Arrays.asList(Objects.requireNonNull(jobArchiveRoot.list())));

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
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
                return new JobUpdateResult(job.toString(), false, e.getMessage());
            }
        };
    }

    private JobUpdateFunction getCreateFunction() {
        return job -> {
            final String jobName = job.toString();
            LOGGER.info("Creating job " + jobName);
            final String jobsRootPath = jobsRoot.getAbsolutePath();
            LOGGER.info("Creating directory " + jobName + " in " + jobsRootPath);
            final File jobDir = Paths.get(jobsRootPath, jobName).toFile();
            if (!jobDir.mkdir()) {
                throw new IOException("Could't create file: " + jobDir.getAbsolutePath());
            }
            final String jobDirPath = jobDir.getAbsolutePath();
            LOGGER.info("Creating file " + JENKINS_JOB_CONFIG_FILE + " in " + jobDirPath);
            Utils.writeToFile(
                    Paths.get(jobDirPath, JENKINS_JOB_CONFIG_FILE),
                    job.generateTemplate()
            );
            throwFromJenkinsResult(JenkinsCliWrapper.getCli().reloadOrRegisterManuallyUploadedJob(jobsRoot, jobName));
            return new JobUpdateResult(jobName, true);
        };
    }

    private JobUpdateFunction getReviveFunction() {
        return job -> {
            final String jobName = job.toString();
            final File src = Paths.get(jobArchiveRoot.getAbsolutePath(), job.toString()).toFile();
            final File dst = Paths.get(jobsRoot.getAbsolutePath(), job.toString()).toFile();
            LOGGER.info("Reviving job " + jobName);
            LOGGER.info("Moving directory " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
            Utils.moveFile(src, dst);
            throwFromJenkinsResult(JenkinsCliWrapper.getCli().reloadOrRegisterManuallyUploadedJob(jobsRoot, jobName));
            return new JobUpdateResult(jobName, true);
        };
    }

    private JobUpdateFunction getArchiveFunction() {
        return job -> {
            final String jobName = job.toString();
            final File src = Paths.get(jobsRoot.getAbsolutePath(), job.toString()).toFile();
            final File dst = Paths.get(jobArchiveRoot.getAbsolutePath(), job.toString()).toFile();
            LOGGER.info("Archiving job " + jobName);
            LOGGER.info("Moving directory " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
            Utils.moveFile(src, dst);
            throwFromJenkinsResult(JenkinsCliWrapper.getCli().deleteJobs(jobName));
            return new JobUpdateResult(jobName, true);
        };
    }

    private JobUpdateFunction getRewriteFunction() {
        return job -> {
            final String jobName = job.toString();
            final File jobConfig = Paths.get(jobsRoot.getAbsolutePath(), jobName, JENKINS_JOB_CONFIG_FILE).toFile();
            LOGGER.info("Rewriting job " + jobName);
            LOGGER.info("Writing to file " + jobConfig.getAbsolutePath());
            Utils.writeToFile(jobConfig, job.generateTemplate());
            throwFromJenkinsResult(JenkinsCliWrapper.getCli().reloadOrRegisterManuallyUploadedJob(jobsRoot, jobName));
            return new JobUpdateResult(jobName, true);
        };
    }

    private void throwFromJenkinsResult(JenkinsCliWrapper.ClientResponse r) throws IOException {
        if (r.sshEngineExeption != null) {
            throw new IOException("Probable ssh engine fail in " + r.cmd, r.sshEngineExeption);
        } else {
            if (r.remoteCommandreturnValue != 0) {
                throw new IOException("ssh command " + r.cmd + " returned non sero" + r.remoteCommandreturnValue);
            }
        }
    }

    interface JobUpdateFunction {
        JobUpdateResult apply(Job job) throws IOException;
    }
}
