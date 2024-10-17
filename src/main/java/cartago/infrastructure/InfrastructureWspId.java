/**
 * CArtAgO - DISI, University of Bologna
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package cartago.infrastructure;

import java.net.URI;

import cartago.WorkspaceId;

/**
 * Identifier of a infrastructure workspace
 *  
 * @author aricci
 */
public class InfrastructureWspId extends  WorkspaceId {

	/* this is the local name, unique in the scope of the parent */
	private URI uri;
		
	/**
	 * 
	 * 
	 * Workspace identifier for Infrastructure workspace.
	 * 
	 * @param fullName full name of the workspace: e.g. /main/w0
	 * @param id unique UUID identifying the workspace
	 * 
	 */
	public InfrastructureWspId(URI uri) {
		super(uri.toString());
		this.uri = uri;
	}
	
	public URI getURI() {
		return uri;
	}
	
	/**
	 * 
	 * Get the local name of the workspace
	 * 
	 * @return
	 */
	public String getName(){
		return uri.toString();
	}
	
	/**
	 * 
	 * Used when the port is defined dynamically.
	 * @param newURI
	 */
	public void finalizeURI(URI newURI) {
		uri = newURI;
	}
	
	@Override
	public boolean equals(Object obj){
		return (obj instanceof InfrastructureWspId) && ((InfrastructureWspId)obj).uri.equals(uri); 
	}
	
	public String toString(){
		return uri.toString();
	}
}
