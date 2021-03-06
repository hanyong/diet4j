//
// The rights holder(s) license this file to you under the
// Apache License, Version 2.0 (the "License"); you may not
// use this file except in compliance with the License. You
// may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// For information about copyright ownership, see the NOTICE
// file distributed with this work.
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package org.diet4j.core;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects functionality common to implementations of ModuleRegistry.
 */
public abstract class AbstractModuleRegistry
    extends
        ModuleRegistry
{
    /**
     * Constructor.
     *
     * @param doNotLoadClassPrefixes prefixes of classes always to be loaded through the system class loader, not this one
     */
    protected AbstractModuleRegistry(
            String [] doNotLoadClassPrefixes )
    {
        theDoNotLoadClassPrefixes = doNotLoadClassPrefixes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ModuleMeta determineSingleResolutionCandidate(
            ModuleRequirement req )
        throws
            NoModuleResolutionCandidateException,
            ModuleResolutionCandidateNotUniqueException
    {
        ModuleMeta [] found = determineResolutionCandidates( req );
        switch( found.length ) {
            case 0:
                throw new NoModuleResolutionCandidateException( req );

            case 1:
                return found[0];

            default:
                // if we found different groupIds, we can't decide
                String groupId = found[0].getModuleGroupId();
                for( int i=1 ; i<found.length ; ++i ) {
                    if( !groupId.equals( found[i].getModuleGroupId())) {
                        throw new ModuleResolutionCandidateNotUniqueException( req, found );
                    }
                }
                // otherwise we take the first, which is the most recent version
                return found[0];
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Module resolve(
            ModuleMeta meta )
        throws
            ModuleNotFoundException,
            ModuleResolutionException
    {
        return resolve( meta, true );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Module resolve(
            ModuleMeta meta,
            boolean    recursive )
        throws
            ModuleNotFoundException,
            ModuleResolutionException
    {
        if( meta == null ) {
            throw new NullPointerException( "Cannot resolve null ModuleMeta" );
        }
        synchronized( RESOLVE_LOCK ) {
            Module ret = theModules.get( meta );
            if( ret == null ) {
                Module [] dependentModules = null;
                if( recursive ) {
                    ModuleRequirement [] reqs = meta.getRuntimeModuleRequirements();
                    dependentModules          = new Module[ reqs.length ];

                    for( int i=0 ; i<reqs.length ; ++i ) {
                        Module dependModule = null;
                        ModuleMeta [] dependAds = determineResolutionCandidates( reqs[i] );

                        Exception chained = null;
                        for( int j=0 ; j<dependAds.length ; ++j ) {
                            try {
                                dependModule = resolve( dependAds[j], true );
                                break; // found one that worked

                            } catch( ModuleResolutionException ex ) {
                                if( !reqs[i].isOptional() ) {
                                    chained = ex;
                                }
                            } catch( ModuleNotFoundException ex ) {
                                if( !reqs[i].isOptional() ) {
                                    chained = ex;
                                }
                            }
                        }
                        if( dependModule != null ) {
                            dependentModules[i] = dependModule;
                        } else if( !reqs[i].isOptional() ) {
                            throw new ModuleResolutionException( meta, reqs[i], chained );
                        }
                    }
                }
                ret = meta.createModule( this, getClass().getClassLoader() );

                if( ret != null ) {
                    theModules.put( meta, ret );

                    if( recursive ) {
                        theForwardRuntimeDependencies.put( ret, dependentModules );

                        for( int i=0 ; i<dependentModules.length ; ++i ) {
                            Module [] uses    = theRuntimeUses.get( dependentModules[i] );
                            Module [] newUses = null;

                            if( uses == null ) {
                                newUses = new Module[] { ret };
                            } else {
                                // append one if not there already
                                boolean found = false;
                                for( int j=0 ; j<uses.length ; ++j ) {
                                    if( ret == uses[j] ) {
                                        found = true;
                                        break;
                                    }
                                }
                                if( !found ) {
                                    newUses = new Module[ uses.length+1 ];
                                    System.arraycopy( uses, 0, newUses, 0, uses.length );
                                    newUses[uses.length] = ret;
                                }
                            }
                            if( dependentModules[i] != null ) {
                                theRuntimeUses.put( dependentModules[i], newUses );
                            }
                        }
                    }
                } else {
                    throw new NullPointerException( "createModule returned null" );
                }
            }
            return ret;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Module getResolutionOf(
            ModuleMeta meta )
    {
        synchronized( RESOLVE_LOCK ) {
            return theModules.get( meta );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Module [] determineRuntimeDependencies(
            Module theModule )
    {
        synchronized( RESOLVE_LOCK ) {
            return theForwardRuntimeDependencies.get( theModule );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Module [] determineRuntimeUses(
            Module theModule )
    {
        synchronized( RESOLVE_LOCK ) {
            return theRuntimeUses.get( theModule );
        }
    }

    /**
     * ModuleRegistries can also acts as a factory for the Modules' ClassLoaders.
     *
     * @param module the Module for which to create a ClassLoader
     * @param parentClassLoader the ClassLoader to use as the parent ClassLoader
     * @return the ClassLoader to use with the Module
     */
    @Override
    protected ClassLoader createClassLoader(
            Module      module,
            ClassLoader parentClassLoader )
    {
        ClassLoader ret = parentClassLoader;

        try {
            Module []            dependencies           = module.getModuleRegistry().determineRuntimeDependencies( module );
            ModuleClassLoader [] dependencyClassLoaders = new ModuleClassLoader[ dependencies.length ];

            for( int i=0 ; i<dependencies.length ; ++i ) {
                if( dependencies[i] != null ) {
                    dependencyClassLoaders[i] = (ModuleClassLoader)dependencies[i].getClassLoader();
                } else {
                    dependencyClassLoaders[i] = null;
                }
            }
            ret = new ModuleClassLoader( module, parentClassLoader, dependencyClassLoaders, theDoNotLoadClassPrefixes );

        } catch( MalformedURLException ex ) {
            log.log( Level.SEVERE, "Failed to create ModuleClassLoader for " + module, ex );
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void addModuleRegistryListener(
            ModuleRegistryListener newListener )
    {
        if( theModuleRegistryListeners == null ) {
            theModuleRegistryListeners = new ArrayList<>();
        }
        theModuleRegistryListeners.add( newListener );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void removeModuleRegistryListener(
            ModuleRegistryListener oldListener )
    {
        theModuleRegistryListeners.remove( oldListener );
    }

    /**
     * Send an event to our listeners.
     *
     * @param theEvent the event to send
     */
    protected final void fireModuleAddedEvent(
            ModuleRegistryEvent theEvent )
    {
        Iterator<ModuleRegistryListener> theIter;
        synchronized( this ) {
            if( theModuleRegistryListeners == null || theModuleRegistryListeners.isEmpty() ) {
                return;
            }
            theIter = ( new ArrayList<>( theModuleRegistryListeners )).iterator();
        }

        while( theIter.hasNext() ) {
            ModuleRegistryListener current = theIter.next();
            current.newModuleAvailable( theEvent );
        }
    }

    /**
     * Obtain String representation.
     *
     * @return String representation
     */
    @Override
    public String toString()
    {
        return getClass().getName() + "(" + nameSet().size() + " known modules)";
    }

    /**
     * The set of currently available Modules, keyed by ModuleMeta.
     */
    private final HashMap<ModuleMeta,Module> theModules = new HashMap<>();

    /**
     * This maps from Module to Module[], reflecting the dependency of one
     * Module on a set of others at run-time. This is the inverse relationship of what
     * is captured in theRuntimeUses.
     *
     * The sequence of the items in the Module[] is the same as the sequence
     * of the items in the ModuleMeta's ModuleRequirements array.
     */
    private final HashMap<Module,Module[]> theForwardRuntimeDependencies = new HashMap<>();

    /**
     * This maps from Module to Module[], reflecting the use of one Module
     * by a set of others. This is the inverse relationship of what is
     * captured in theForwardRuntimeDependencies.
     */
    private final HashMap<Module,Module[]> theRuntimeUses = new HashMap<>();

    /**
     * The set of currently subscribed ModuleRegistryListeners. Allocated as needed.
     */
    private ArrayList<ModuleRegistryListener> theModuleRegistryListeners = null;

    /**
     * Always load classes with these prefixes through the default ClassLoader.
     */
    protected String [] theDoNotLoadClassPrefixes;

    /**
     * This object is used as a semaphore for Module loads.
     */
    protected final Object RESOLVE_LOCK = new Object();

    /**
     * Logger.
     */
    private static final Logger log = Logger.getLogger( AbstractModuleRegistry.class.getName() );

    /**
     * Only load classes with this prefix from the default ClassLoader.
     * It would be really nice if those were easier to determine, but javax.servlet,
     * for example, is defined in Jetty as well.
     */
    public static final String [] DEFAULT_DO_NOT_LOAD_CLASS_PREFIXES = {
        "java.", // java
        "javax.a",
        "javax.c",
        "javax.i",
        "javax.j",
        "javax.l",
        "javax.m",
        "javax.n",
        "javax.p",
        "javax.r",
        "javax.script", // not servlet
        "javax.sec",
        "javax.so",
        "javax.sql",
        "javax.swing",
        "javax.t",
        "javax.x",
        "com.sun.",
        "sun", // sun, sunw
        "org.diet4j.cmdline",
        "org.diet4j.core",
        "org.diet4j.tomcat",
        "org.ietf.jgss",
        "org.omg.",
        "org.w3c.dom",
        "org.xml.sax"
    };
}
