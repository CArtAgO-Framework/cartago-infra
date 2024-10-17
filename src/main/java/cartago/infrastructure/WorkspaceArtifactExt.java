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

import cartago.AgentBody;
import cartago.AgentCredential;
import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpExecutionFrame;
import cartago.OpFeedbackParam;
import cartago.Workspace;
import cartago.WorkspaceId;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

/**
 * Artifact providing basic functionalities to manage the workspace, including
 * creating new artifacts, lookup artifacts, setting RBAC security policies, and
 * so on.
 * 
 * @author aricci
 *
 */
public class WorkspaceArtifactExt extends Artifact {

	private Workspace wsp;

	private HashMap<String,WorkspaceDescriptor> linkedWsp;

	@OPERATION
	void init(Workspace env) {
		linkedWsp = new HashMap<String,WorkspaceDescriptor>();
		this.wsp = env;
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (wsp != null) {
                	System.out.println("[CArtAgo Environment] shutting down workspace processes...");
                	linkedWsp.forEach((k,e) -> {
                			if (e.isChild()) {
                				log("shutting down " + k);
                				e.getWspProcess().destroy();
                			}
                		});
                }
            }
		});
	
		
	}

	/***********************************************************************************************
	 * 
	 * Methods for managing Linked workspaces
	 * 
	 ***********************************************************************************************/
	
	/**
	 * Create a new workspace
	 * 
	 * @param uri
	 * @param localRef
	 * @throws CartagoInfrastructureLayerException
	 */
	public void createLinkedWorkspace(String wspName, int port, String localRef) throws CartagoInfrastructureLayerException {
		try {
			String cp = System.getProperty("java.class.path");
			// Process p = Runtime.getRuntime().exec("java -cp " + cp + " cartago.CartagoEnvironment " + uri);
			ProcessBuilder builder = new ProcessBuilder("java", "-cp", cp, "cartago.Workspace", "-name", wspName, "-port", ""+port );	
			Process p = builder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			boolean ok = false;
			
			long t0 = System.currentTimeMillis();
			while (System.currentTimeMillis() - t0 < 100000) {
				if (reader.ready()) {
					var msg = reader.readLine();
					if (msg.startsWith(cartago.infrastructure.CartagoDistributedEnvironment.READY_MSG)) {
						ok = true;
						break;
					}
				} 
				Thread.sleep(100);
			}
			
			if (ok) {
				URI uri = URI.create("http://localhost:" + port + "/" + wspName);
				InfrastructureWspId wspId = new InfrastructureWspId(uri);
				WorkspaceDescriptor des = new WorkspaceDescriptor(wspId, localRef, p);
				this.linkedWsp.put(localRef, des);
			} else {
				throw new CartagoInfrastructureLayerException();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new CartagoInfrastructureLayerException();
		}
	}
	
	/**
	 * 
	 * Link an existing workspace.
	 * 
	 * @param wspURI
	 * @param localWspRef
	 * @throws CartagoInfrastructureLayerException
	 */
	public void linkWorkspace(URI wspURI, String localWspRef) throws CartagoInfrastructureLayerException {
		try { 
			InfrastructureWspId wspId = new InfrastructureWspId(wspURI);
			WorkspaceDescriptor des = new WorkspaceDescriptor(wspId, localWspRef);
			this.linkedWsp.put(localWspRef, des);			
		} catch (Exception ex) {
			throw new CartagoInfrastructureLayerException();
		}
	}
	
	/**
	 * 
	 * Get the workspace identifier of a linked workspace.
	 * 
	 * 
	 * @param localWspRef
	 * @return
	 */
	public Optional<InfrastructureWspId> getLinkedWspURI(String localWspRef) {
		WorkspaceDescriptor des = linkedWsp.get(localWspRef);
		if (des == null) {
			return Optional.empty();
		} else {
			return Optional.of(des.getWorkspaceId());
		}
	}
	/**
	 * Join a workspace
	 * 
	 * @param wspRef workspace absolute name
	 * @param res    output parameter: workspace id
	 */
	@OPERATION
	void joinWorkspace(String wspURI, OpFeedbackParam<WorkspaceId> res) {
		try {
			// wspRef must be absolute: /...
			URI uri = URI.create(wspURI);
			this.joinWorkspace(uri, res);
		} catch (Exception ex) {
			// ex.printStackTrace();
			failed("Join Workspace error: " + ex.getMessage());
		}
	}

	/**
	 * Join a linked workspace
	 * 
	 * @param wspRef workspace absolute name
	 * @param res    output parameter: workspace id
	 */
	@OPERATION
	void joinLinkedWorkspace(String localRef, OpFeedbackParam<WorkspaceId> res) {
		try {
			Optional<InfrastructureWspId> uri = getLinkedWspURI(localRef);
			if (uri.isPresent()) {
				this.joinWorkspace(uri.get().getURI(), res);
			} else {
				failed("Join Linked Workspace error: unknown wsp");
			}
		} catch (Exception ex) {
			// ex.printStackTrace();
			failed("Join Workspace error: " + ex.getMessage());
		}
	}

	/**
	 * Join a workspace
	 * 
	 * @param wspRef workspace absolute name
	 * @param res    output parameter: workspace id
	 */
	private void joinWorkspace(URI wspURI, OpFeedbackParam<WorkspaceId> res) {
		try {
			WorkspaceId wspId = new InfrastructureWspId(wspURI);

			OpExecutionFrame opFrame = this.getOpFrame();

			AgentBody body = wsp.getAgentBody(getCurrentOpAgentId());
			AgentCredential cred = body.getAgentCredential();

			String sid = CartagoDistributedEnvironment.getInstance().requestToJoinWorkspace(wspURI, cred);

			wsp.notifyJoinWSPRequestCompleted(body.getAgentCallback(), opFrame.getActionId(),
					opFrame.getSourceArtifactId(), opFrame.getOperation(), wspId, sid);

			res.set(wspId);

		} catch (Exception ex) {
			// ex.printStackTrace();
			failed("Join Workspace error: " + ex.getMessage());
		}
	}	
	
	/**
	 * 
	 * Create a linked workspace
	 * 
	 * @param wspURI URI of the workspace
	 */
	@OPERATION
	void createLinkedWorkspace(String name, int port) {
		try {
			createLinkedWorkspace(name, port, name);
		} catch (Exception ex) {
			failed("Workspace creation error");
		}
	}

	/**
	 * 
	 * Create a linked workspace
	 * 
	 * @param wspURI URI of the workspace, finding a port  
	 */
	@OPERATION
	void createLinkedWorkspace(String name) {
		try {
			createLinkedWorkspace(name, 0, name);
		} catch (Exception ex) {
			failed("Workspace creation error");
		}
	}

	/**
	 * 
	 * Link an existing workspace
	 * 
	 * @param wspURI URI of the workspace
	 */
	@OPERATION
	void linkWorkspace(String wspURI, String localRef) {
		try {
			URI wspId = URI.create(wspURI);
			linkWorkspace(wspId, localRef);
		} catch (Exception ex) {
			failed("Workspace creation error");
		}
	}

}
