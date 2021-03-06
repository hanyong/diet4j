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

package org.diet4j.tomcat;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.loader.WebappLoader;
import org.diet4j.core.Module;
import org.diet4j.core.ModuleMeta;
import org.diet4j.core.ModuleRegistry;
import org.diet4j.core.ModuleRequirement;
import org.diet4j.core.ModuleResolutionException;
import org.diet4j.core.ScanningDirectoriesModuleRegistry;

/**
 * This Tomcat-specific class can be specified as a WebappLoader in a Tomcat context.xml file in order
 * to make the entire Tomcat app use diet4j.
 * Note that the diet4j JARs must be accessible to Tomcat. Apparently bundling them with a web app's WAR
 * file is not sufficient, and they must be Tomcat's library path.
 *
 * Use as follows in context.xml:
 *
 * <pre>
 * &lt;Context path='/context-path'&gt;
 *  &lt;Loader className='org.diet4j.tomcat.TomcatModuleLoader'
 *          rootmodule='com.example.mywebapp'
 *          moduledirectory='/usr/share/java' /&gt;
 * &lt;/Context&gt;
 * </pre>
 * where <code>com.example.mywebapp</code> is the the name of the root module for this app,
 * and <code>moduledirectory</code> is the directory where JARs of Modules can be found.
 */
public class TomcatModuleLoader
    extends
        WebappLoader
{
    /**
     * Public constructor.
     */
    public TomcatModuleLoader()
    {
        this( null );
    }

    /**
     * Constructor.
     *
     * @param parent the parent ClassLoader
     */
    public TomcatModuleLoader(
            ClassLoader parent )
    {
        super( parent );

        setLoaderClass( TomcatWebAppClassLoader.class.getName() );
    }

    /**
     * Set name of root module.
     *
     * @param newValue the value
     * @see #getRootmodule
     */
    public void setRootmodule(
            String newValue )
    {
        theRootmodule = newValue;
    }

    /**
     * Get name of root module.
     *
     * @return the value
     * @see #setRootmodule
     */
    public String getRootmodule()
    {
        return theRootmodule;
    }

    /**
     * Set directory in which the module JARs can be found.
     *
     * @param newValue the value
     * @see #getModuledirectory
     */
    public void setModuledirectory(
            String newValue )
    {
        theModuledirectory = newValue;
    }

    /**
     * Get directory in which the module JARs can be found.
     *
     * @return the value
     * @see #setModuledirectory
     */
    public String getModuledirectory()
    {
        return theModuledirectory;
    }

    /**
     * Start it.
     *
     * @throws LifecycleException thrown if this TomcatModuleLoader cannot be started
     */
    @Override
    public void startInternal()
        throws
            LifecycleException
    {
        if( theRootmodule == null ) {
            throw new LifecycleException( "rootmodule parameter not set" );
        }

        Map<File,String> dirs = new HashMap<>();
        for( String dir : theModuledirectory.split( File.pathSeparator )) {
            try {
                File fileDir = new File( dir ).getCanonicalFile();
                if( dirs.put( fileDir, dir ) != null ) {
                    throw new LifecycleException( "Directory (indirectly?) specified more than once in moduledirectory parameter: " + dir );
                }
            } catch( IOException ex ) {
                throw new LifecycleException( "Directory specified in moduledirectory cannot be resolved into a canonical path: " + dir );
            }
        }
        File [] dirArray = new File[ dirs.size() ];
        dirs.keySet().toArray( dirArray );

        theModuleRegistry = ScanningDirectoriesModuleRegistry.create( dirArray, TOMCAT_DO_NOT_LOAD_CLASS_PREFIXES );

        // I would have liked to invoke super.startInternal() last but that's the only way I can get at our ClassLoader.
        super.startInternal();

        TomcatWebAppClassLoader myClassLoader = (TomcatWebAppClassLoader) super.getClassLoader();

        try {
            ModuleRequirement rootRequirement = ModuleRequirement.parse( theRootmodule );

            ModuleMeta foundRootMeta   = theModuleRegistry.determineSingleResolutionCandidate( rootRequirement );
            Module     foundRootModule = theModuleRegistry.resolve( foundRootMeta );

            myClassLoader.initialize( foundRootModule.determineRuntimeDependencies() );

            foundRootModule.activateRecursively();
                    // may throw an exception

        } catch( ModuleResolutionException ex ) {
            // construct a readable error message
            StringBuilder msg    = new StringBuilder();
            StringBuilder indent = new StringBuilder();

            msg.append( "diet4j initialization failed. Cannot resolve requirement " );
            for( Throwable current = ex ; current != null ; current = current.getCause() ) {
                msg.append( indent );

                if( current instanceof ModuleResolutionException ) {
                    msg.append( ((ModuleResolutionException)current).getModuleRequirement().toString() );
                } else {
                    msg.append( ex.getMessage() );
                }
                if( indent.length() == 0 ) {
                    indent.append( "\ndepending on:  " );
                } else {
                    indent.append( "  " );
                }
            }

            throw new LifecycleException( msg.toString() );
        } catch( Throwable ex ) {
            throw new LifecycleException( ex );
        }
    }

    /**
     * Stop it.
     *
     * @throws LifecycleException thrown if this TomcatModuleLoader cannot be stopped
     */
    @Override
    public void stopInternal()
        throws
            LifecycleException
    {
        super.setState( LifecycleState.STOPPING );
    }

    /**
     * Convert to String, for debugging.
     *
     * @return String form
     */
    @Override
    public String toString()
    {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    /**
     * Name of the root module.
     */
    protected String theRootmodule;

    /**
     * Directory in which the module JARs can be found.
     */
    protected String theModuledirectory = DEFAULT_MODULEDIRECTORY;

    /**
     * Keep a reference to the ModuleRegistries that we are using so they won't be garbage collected.
     */
    protected ModuleRegistry theModuleRegistry;

    /**
     * The default directory in which the module JARs can be found.
     */
    public static final String DEFAULT_MODULEDIRECTORY = "/usr/lib/java";

    /**
     * Tomcat bundles certain libraries, so it needs a different set of class name prefixes
     * not to load via diet4j.
     */
    public static final String [] TOMCAT_DO_NOT_LOAD_CLASS_PREFIXES = {
        "java.", // java
        "javax.",
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
