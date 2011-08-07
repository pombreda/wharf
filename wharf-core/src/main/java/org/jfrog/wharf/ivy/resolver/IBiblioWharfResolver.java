/*
 *
 *  Copyright (C) 2010 JFrog Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package org.jfrog.wharf.ivy.resolver;

import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;
import org.jfrog.wharf.ivy.model.ModuleRevisionMetadata;
import org.jfrog.wharf.ivy.repository.WharfURLRepository;
import org.jfrog.wharf.ivy.util.WharfUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Tomer Cohen
 */
public class IBiblioWharfResolver extends IBiblioResolver implements WharfResolver {

    protected CacheTimeoutStrategy snapshotTimeout = DAILY;

    public IBiblioWharfResolver() {
        WharfUtils.hackIvyBasicResolver(this);
    }

    @Override
    public void setRepository(Repository repository) {
        super.setRepository(repository);
    }

    public WharfURLRepository getWharfUrlRepository() {
        return (WharfURLRepository) super.getRepository();
    }

    @Override
    public Artifact fromSystem(Artifact artifact) {
        return super.fromSystem(artifact);
    }

    @Override
    public void setChecksums(String checksums) {
        getWharfUrlRepository().setChecksums(checksums);
    }

    public boolean supportsWrongSha1() {
        return getWharfUrlRepository().supportsWrongSha1();
    }

    @Override
    public String[] getChecksumAlgorithms() {
        return getWharfUrlRepository().getChecksumAlgorithms();
    }

    @Override
    public ResolvedResource getArtifactRef(Artifact artifact, Date date) {
        ResolvedResource artifactRef = super.getArtifactRef(artifact, date);
        return WharfUtils.convertToWharfResource(artifactRef);
    }

    @Override
    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        ResolvedResource ivyFileRef = super.findIvyFileRef(dd, data);
        return WharfUtils.convertToWharfResource(ivyFileRef);
    }

    /**
     * Returns the timeout strategy for a Maven Snapshot in the cache
     */
    public CacheTimeoutStrategy getSnapshotTimeout() {
        return snapshotTimeout;
    }

    /**
     * Sets the time in ms a Maven Snapshot in the cache is not checked for a newer version
     *
     * @param snapshotLifetime The lifetime in ms
     */
    public void setSnapshotTimeout(long snapshotLifetime) {
        this.snapshotTimeout = new Interval(snapshotLifetime);
    }

    /**
     * Sets a timeout strategy for a Maven Snapshot in the cache
     *
     * @param cacheTimeoutStrategy The strategy
     */
    public void setSnapshotTimeout(CacheTimeoutStrategy cacheTimeoutStrategy) {
        this.snapshotTimeout = cacheTimeoutStrategy;
    }

    @Override
    public long get(Resource resource, File dest) throws IOException {
        return super.get(resource, dest);
    }

    @Override
    protected ResolvedModuleRevision findModuleInCache(DependencyDescriptor dd, ResolveData data) {
        setChangingPattern(null);
        ResolvedModuleRevision moduleRevision = WharfUtils.findModuleInCache(this, dd, data);
        if (moduleRevision == null) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        }
        ModuleRevisionMetadata metadata = getCacheProperties(moduleRevision.getId());
        // See http://issues.gradle.org/browse/GRADLE-1721 for some null metadata even after found in cache ?!?
        if (metadata == null || snapshotTimeout.isCacheTimedOut(getLastResolvedTime(metadata))) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        } else {
            return moduleRevision;
        }
    }

    public ResolvedModuleRevision basicFindModuleInCache(DependencyDescriptor dd, ResolveData data, boolean anyResolver) {
        return super.findModuleInCache(dd, data, anyResolver);
    }

    @Override
    public CacheMetadataOptions getCacheOptions(ResolveData data) {
        return super.getCacheOptions(data);
    }

    public ModuleRevisionMetadata getCacheProperties(ModuleRevisionId mrid) {
        WharfCacheManager cacheManager = (WharfCacheManager) getRepositoryCacheManager();
        return cacheManager.getMetadataHandler().getModuleRevisionMetadata(mrid);
    }

    @Override
    public long getAndCheck(Resource resource, File dest) throws IOException {
        return WharfUtils.getAndCheck(this, resource, dest);
    }

    private Long getLastResolvedTime(ModuleRevisionMetadata cacheProperties) {
        String lastResolvedProp = cacheProperties.latestResolvedTime;
        Long lastResolvedTime = lastResolvedProp != null ? Long.parseLong(lastResolvedProp) : 0;
        return lastResolvedTime;
    }

    @Override
    public void setRoot(String root) {
        super.setRoot(root);

        URI rootUri;
        try {
            rootUri = new URI(root);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (rootUri.getScheme().equalsIgnoreCase("file")) {
            setSnapshotTimeout(ALWAYS);
        } else {
            setSnapshotTimeout(DAILY);
        }
    }

    public interface CacheTimeoutStrategy {
        boolean isCacheTimedOut(long lastResolvedTime);
    }

    public static class Interval implements CacheTimeoutStrategy {
        private long interval;

        public Interval(long interval) {
            this.interval = interval;
        }

        public boolean isCacheTimedOut(long lastResolvedTime) {
            return System.currentTimeMillis() - lastResolvedTime > interval;
        }
    }

    public static final CacheTimeoutStrategy NEVER = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return false;
        }
    };

    public static final CacheTimeoutStrategy ALWAYS = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return true;
        }
    };

    public static final CacheTimeoutStrategy DAILY = new CacheTimeoutStrategy() {
        public boolean isCacheTimedOut(long lastResolvedTime) {
            Calendar calendarCurrent = Calendar.getInstance();
            calendarCurrent.setTime(new Date());
            int dayOfYear = calendarCurrent.get(Calendar.DAY_OF_YEAR);
            int year = calendarCurrent.get(Calendar.YEAR);

            Calendar calendarLastResolved = Calendar.getInstance();
            calendarLastResolved.setTime(new Date(lastResolvedTime));
            if (calendarLastResolved.get(Calendar.YEAR) == year &&
                    calendarLastResolved.get(Calendar.DAY_OF_YEAR) == dayOfYear) {
                return false;
            }
            return true;
        }
    };

    @Override
    public int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + getRoot().hashCode();
        result = 31 * result + snapshotTimeout.hashCode();
        result = 31 * result + getPattern().hashCode();
        result = 31 * result + (isUsepoms() ? 1 : 0);
        result = 31 * result + (isAlwaysCheckExactRevision() ? 1 : 0);
        result = 31 * result + (isM2compatible() ? 1 : 0);
        result = 31 * result + getWharfUrlRepository().getChecksums().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IBiblioWharfResolver)) return false;

        IBiblioWharfResolver that = (IBiblioWharfResolver) o;

        if (!getName().equals(that.getName())
                || !getRoot().equals(that.getRoot())
                || !snapshotTimeout.equals(that.snapshotTimeout)
                || !getPattern().equals(that.getPattern())
                || isUsepoms() != that.isUsepoms()
                || isUseMavenMetadata() != that.isUseMavenMetadata()
                || isAlwaysCheckExactRevision() != that.isAlwaysCheckExactRevision()
                || isM2compatible() != that.isM2compatible()
                || !getWharfUrlRepository().getChecksums().equals(that.getWharfUrlRepository().getChecksums()))
            return false;
        return true;
    }
}
