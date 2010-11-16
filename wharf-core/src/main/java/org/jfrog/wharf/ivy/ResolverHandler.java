package org.jfrog.wharf.ivy;

import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.IvySettingsAware;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tomer Cohen
 */
public class ResolverHandler implements IvySettingsAware {

    /**
     * This is the directory where you have the right to put all of the needed files for the handler. Default is
     * cacheDir/.wharf
     */
    private final File baseDir;

    private final Map<Integer, WharfResolver> resolvers = new HashMap<Integer, WharfResolver>();
    private final Map<Integer, WharfResolver> resolverFromDependencyResolverHash =
            new HashMap<Integer, WharfResolver>();
    private CachedResolversFile cachedResolversFile;
    private IvySettings settings;
    private static final WharfResolver LOCAL_WHARF = new WharfResolver("local-wharf", "wharf");

    public ResolverHandler(File baseDir) {
        this.baseDir = baseDir;
        // populate the set of resolvers from the baseDir/resolvers.json file
        if (cachedResolversFile == null) {
            cachedResolversFile = new CachedResolversFile(baseDir);
            Set<WharfResolver> resolverIds = cachedResolversFile.getWharfResolvers();
            for (WharfResolver wharfResolver : resolverIds) {
                resolvers.put(wharfResolver.hashCode(), wharfResolver);
            }
        }
    }

    /**
     * @return Get a resolver ID according
     */
    public WharfResolver getResolver(DependencyResolver resolver) {
        // find in shortcut
        int hash = resolver.hashCode();
        if (resolverFromDependencyResolverHash.containsKey(hash)) {
            return resolverFromDependencyResolverHash.get(hash);
        }
        // Need to find if in my cache then save to json file and shortcut
        WharfResolver wharfResolver = new WharfResolver(resolver);
        resolverFromDependencyResolverHash.put(hash, wharfResolver);
        resolvers.put(wharfResolver.hashCode(), wharfResolver);
        saveCacheResolverFile();
        return wharfResolver;
    }

    public WharfResolver getLocalResolver() {
        if (!resolvers.containsKey(LOCAL_WHARF.hashCode())) {
            try {
                LOCAL_WHARF.url = baseDir.toURI().toURL().toExternalForm();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            resolvers.put(LOCAL_WHARF.hashCode(), LOCAL_WHARF);
        }
        return LOCAL_WHARF;
    }

    public void cleanResolvers() {
        resolvers.clear();
        getLocalResolver();
        saveCacheResolverFile();
    }

    /**
     * @return set of all resolvers Ids ever used in this cache
     */
    public Collection<WharfResolver> getAllResolvers() {
        return resolvers.values();
    }

    public WharfResolver getResolver(int resolverId) {
        return resolvers.get(resolverId);
    }

    public void saveCacheResolverFile() {
        cachedResolversFile.save();
    }

    public void setSettings(IvySettings settings) {
        this.settings = settings;
    }

    public boolean isActiveResolver(int resolverId) {
        WharfResolver resolver = getResolver(resolverId);
        if (resolver == null) {
            // TODO: Log ivy errors this is not possible someone ruined the cache
            return false;
        }
        if (settings.getResolverNames().contains(resolver.name)) {
            int currentResolverId = new WharfResolver(settings.getResolver(resolver.name)).getId();
            return currentResolverId == resolverId;
        }
        return false;
    }

    public boolean contains(int resolverId) {
        return resolvers.containsKey(resolverId);
    }

    public void removeResolver(int resolverIdHashCode) {
        if (resolvers.containsKey(resolverIdHashCode)) {
            resolvers.remove(resolverIdHashCode);
            resolverFromDependencyResolverHash.remove(resolverIdHashCode);
            saveCacheResolverFile();
        }
    }
}
