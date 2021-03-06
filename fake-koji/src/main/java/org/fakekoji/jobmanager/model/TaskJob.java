package org.fakekoji.jobmanager.model;

import org.fakekoji.model.BuildProvider;
import org.fakekoji.model.Platform;
import org.fakekoji.model.Product;
import org.fakekoji.model.Task;
import org.fakekoji.model.TaskVariantValue;
import org.fakekoji.model.TaskVariant;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fakekoji.jobmanager.JenkinsJobTemplateBuilder.JDK_VERSION_VAR;
import static org.fakekoji.jobmanager.JenkinsJobTemplateBuilder.OJDK_VAR;
import static org.fakekoji.jobmanager.JenkinsJobTemplateBuilder.PACKAGE_NAME_VAR;
import static org.fakekoji.jobmanager.JenkinsJobTemplateBuilder.PROJECT_NAME_VAR;

public abstract class TaskJob extends Job {

    private final String projectName;
    private final Product product;
    private final Set<BuildProvider> buildProviders;
    private final Task task;
    private final Platform platform;
    private final Map<TaskVariant, TaskVariantValue> variants;
    private final File scriptsRoot;

    TaskJob(
            String projectName,
            Product product,
            Set<BuildProvider> buildProviders,
            Task task,
            Platform platform,
            Map<TaskVariant, TaskVariantValue> variants,
            File scriptsRoot
    ) {
        this.projectName = projectName;
        this.product = product;
        this.buildProviders = buildProviders;
        this.task = task;
        this.platform = platform;
        this.variants = variants;
        this.scriptsRoot = scriptsRoot;
    }

    public String getProjectName() {
        return projectName;
    }

    public Product getProduct() {
        return product;
    }

    public Set<BuildProvider> getBuildProviders() {
        return buildProviders;
    }

    public Task getTask() {
        return task;
    }

    public Platform getPlatform() {
        return platform;
    }

    public Map<TaskVariant, TaskVariantValue> getVariants() {
        return variants;
    }

    public File getScriptsRoot() {
        return scriptsRoot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskJob)) return false;
        TaskJob taskJob = (TaskJob) o;
        return Objects.equals(projectName, taskJob.projectName) &&
                Objects.equals(product, taskJob.product) &&
                Objects.equals(buildProviders, taskJob.buildProviders) &&
                Objects.equals(task, taskJob.task) &&
                Objects.equals(platform, taskJob.platform) &&
                Objects.equals(variants, taskJob.variants) &&
                Objects.equals(scriptsRoot, taskJob.scriptsRoot);
    }

    Map<String, String> getExportedVariables() {
        return new HashMap<String, String>(){{
            put(JDK_VERSION_VAR, product.getVersion());
            put(OJDK_VAR, 'o' + product.getId());
            put(PACKAGE_NAME_VAR, product.getPackageName());
            put(PROJECT_NAME_VAR, getProjectName());
            putAll(getVariants().entrySet()
                    .stream()
                    .collect(Collectors.toMap(key -> key.getKey().getId(), value -> value.getValue().getId())));
        }};
    }

//    static Map<String, String> assembleVariantVariables(Map<TaskVariant, TaskVariantValue> variants) {
//        variants.entrySet().stream()
//                .sorted(Comparator.comparing(Map.Entry::getKey))
//                .map(entry -> entry.getValue().getId())
//                .collect(Collectors.joining(".")) + '.' + getBuildPlatform().assembleString()
//    }
}
