package org.fakekoji.jobmanager.manager;

import org.fakekoji.jobmanager.ManagementException;
import org.fakekoji.jobmanager.ManagementResult;
import org.fakekoji.jobmanager.Manager;
import org.fakekoji.model.BuildProvider;
import org.fakekoji.storage.Storage;
import org.fakekoji.storage.StorageException;

import java.util.List;

public class BuildProviderManager implements Manager<BuildProvider> {

    private final Storage<BuildProvider> storage;

    public BuildProviderManager(final Storage<BuildProvider> storage) {
        this.storage = storage;
    }

    @Override
    public ManagementResult create(BuildProvider buildProvider) throws StorageException, ManagementException {
        if (storage.contains(buildProvider.getId())) {
            throw new ManagementException("Build provider with id " + buildProvider.getId() + " already exists");
        }
        storage.store(buildProvider.getId(), buildProvider);
        return null;
    }

    @Override
    public BuildProvider read(String id) throws StorageException, ManagementException {
        if (!storage.contains(id)) {
            throw new ManagementException("No build provider with id: " + id);
        }
        return storage.load(id, BuildProvider.class);
    }

    @Override
    public List<BuildProvider> readAll() throws StorageException {
        return storage.loadAll(BuildProvider.class);
    }

    @Override
    public ManagementResult update(String id, BuildProvider platform) throws StorageException, ManagementException {
        return null;
    }

    @Override
    public ManagementResult delete(String id) throws StorageException, ManagementException {
        return null;
    }
}

