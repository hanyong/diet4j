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

/**
 * This Exception is thrown if Module activation was unsuccessful.
 */
public class ModuleActivationException
        extends
            ModuleException
{
    private static final long serialVersionUID = 1L; // helps with serialization

    /**
      * Constructor.
      *
      * @param meta the ModuleMeta for the Module whose activation failed.
      * @param org the Throwable that was the cause of this ModuleActivationException
      */
    public ModuleActivationException(
            ModuleMeta meta,
            Throwable  org )
    {
        super( meta, org );
    }

    /**
      * Constructor.
      *
      * @param meta the ModuleMeta for the Module whose activation failed.
      * @param msg a message explaining the cause of this ModuleActivationException
      */
    public ModuleActivationException(
            ModuleMeta meta,
            String     msg )
    {
        super( meta, msg, null );
    }

    /**
      * Constructor.
      *
      * @param meta the ModuleMeta for the Module whose activation failed.
      * @param msg a message explaining the cause of this ModuleActivationException
      * @param org the Throwable that was the cause of this ModuleActivationException
      */
    public ModuleActivationException(
            ModuleMeta meta,
            String     msg,
            Throwable  org )
    {
        super( meta, msg, org );
    }

    /**
     * For debugging.
     *
     * @return a string representation of this object
     */
    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder( 100 ); // fudge
        buf.append( "ModuleActivationException: could not activate Module " );
        if( theModuleMeta != null ) {
            buf.append( theModuleMeta.toString() );
        } else {
            buf.append( "null" );
        }

        if( getMessage() != null ) {
            buf.append( ": ");
            buf.append( getMessage() );
        }
        return buf.toString();
    }
}
