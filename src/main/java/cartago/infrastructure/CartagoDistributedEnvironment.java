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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import cartago.AgentCredential;
import cartago.ArtifactConfig;
import cartago.ArtifactFactory;
import cartago.CARTAGO_VERSION;
import cartago.CartagoException;
import cartago.IAgentSession;
import cartago.ICartagoCallback;
import cartago.ICartagoContext;
import cartago.ICartagoController;
import cartago.ICartagoListener;
import cartago.ICartagoLogger;
import cartago.Workspace;
import cartago.WorkspaceId;
import cartago.tools.inspector.Inspector;


/**
 * 
 * Entry point for working with CArtAgO Environment
 *  
 * @author aricci
 *
 */
public class CartagoDistributedEnvironment {

	/* singleton design */
	private static CartagoDistributedEnvironment instance;
	
	private CartagoInfrastructureLayer infraLayer;
		
	private HashMap<String,Inspector> debuggers;
	
	private Workspace wsp;
	
	public static String WSP_DEFAULT_NAME = "main";
	static String SPLASH_MSG = "CArtAgO Infrastructure v." +CARTAGO_VERSION.getID();
	static String READY_MSG = "CArtAgO Ready.";
	
	public static CartagoDistributedEnvironment getInstance() {
		synchronized (CartagoDistributedEnvironment.class) {
			if (instance == null) {
				instance = new CartagoDistributedEnvironment();
			}
			return instance;
		}
	}
	
	private CartagoDistributedEnvironment() {
		debuggers = new HashMap<String,Inspector>();
		infraLayer = new CartagoInfrastructureLayerImpl();
	}
	
	/* Init methods */
	
	/**
	 * 
	 * Initialise the workspace accessible on the network
	 * 
	 * @param wspName wsp  name
	 * @param localPort local port
	 * @param logger logger to be used
	 * @throws CartagoInfrastructureLayerException
	 */
	public void initWsp(String wspName, int localPort, Optional<ICartagoLogger> logger) throws CartagoInfrastructureLayerException {
		URI wspURI = URI.create("http://localhost:"+localPort+"/"+wspName);
		WorkspaceId id = new InfrastructureWspId(wspURI);
		wsp = new Workspace(id, logger);
		try {
			wsp.addArtifactToDefaultSet("workspace-ext", "cartago.infrastructure.WorkspaceArtifactExt", new ArtifactConfig());
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new CartagoInfrastructureLayerException();
		}
		infraLayer = new CartagoInfrastructureLayerImpl(wspURI);
	}

	/**
	 * 
	 * Initialise the workspace accessible on the network, using the default port
	 * 
	 * @param wspName wsp  name
	 * @param logger logger to be used
	 * @throws CartagoInfrastructureLayerException
	 */
	public void initWsp(String wspName, Optional<ICartagoLogger> logger) throws CartagoInfrastructureLayerException {
		this.initWsp(wspName, CartagoInfrastructureLayerImpl.WSP_DEFAULT_PORT, logger);
	}

	/**
	 * 
	 * Initialise the workspace accessible on the network, using the default name
	 * 
	 * @param wspName wsp  name
	 * @param logger logger to be used
	 * @throws CartagoInfrastructureLayerException
	 */
	public void initWsp(int localPort, Optional<ICartagoLogger> logger) throws CartagoInfrastructureLayerException {
		this.initWsp(CartagoDistributedEnvironment.WSP_DEFAULT_NAME, localPort, logger);
	}
	
	/**
	 * 
	 * Initialise the workspace accessible on the network, using the default name and port
	 * 
	 * @param logger logger to be used
	 * @throws CartagoInfrastructureLayerException
	 */
	public void initWsp(Optional<ICartagoLogger> logger) throws CartagoInfrastructureLayerException {
		this.initWsp(CartagoDistributedEnvironment.WSP_DEFAULT_NAME, CartagoInfrastructureLayerImpl.WSP_DEFAULT_PORT, logger);
	}
	
	
	/**
	 * 
	 * Get the workspace.
	 * 
	 * @return
	 */
	public Workspace getWorkspace() {
		return wsp;
	}
	
	/**
	 * 
	 * Get version.
	 * 
	 * @return
	 */
	public String getVersion() {
		return CARTAGO_VERSION.getID();
	}
	
	/**
	 * 
	 * Shutdown the CArtAgO environmennt.
	 * 
	 * @throws CartagoException
	 */
	public synchronized void shutdown() throws CartagoException {
		infraLayer.shutdownLayer();
	}
	
	// Agent sessions

	/**
	 * 
	 * Start a CArtAgO session in a remote workspace.
	 * 
	 * @param wspURI URI of the workspace: (es: http://acme.org:20100/mywsp)
	 * @param eventListener listener to receive CArtAgO events
	 * @return Session object
	 * @return
	 * @throws CartagoException
	 */
	public synchronized IAgentSession startSession(URI wspURI, AgentCredential cred, ICartagoListener eventListener) throws CartagoException {
			AgentSession session = new AgentSession(cred,null,eventListener);
			String sid  = requestToJoinWorkspace(wspURI, cred);
			WorkspaceId wspId = new InfrastructureWspId(wspURI);
			var ctx = completeJoinWorkspace(wspId,  sid, session);
			session.init(wspId, ctx);
			return session;
	}
	
	
	/**
	 * 
	 * Join a remote workspace
	 * 
	 * @param wspURI workspace URI
	 * @param cred agent credentials
	 * @return
	 * @throws CartagoException
	 */
	public synchronized ICartagoContext joinWorkspace(URI wspURI, AgentCredential cred, ICartagoCallback eventListener) throws CartagoException{
		try {
			String sid  = this.infraLayer.requestToJoinWorkspace(wspURI, cred);
			WorkspaceId wspId = new InfrastructureWspId(wspURI);
			ICartagoContext ctx = this.infraLayer.joinWorkspace(wspId, sid, eventListener);
			return ctx;
		} catch (CartagoInfrastructureLayerException ex) {
			ex.printStackTrace();
			throw new CartagoException("Join " + wspURI + " failed ");
		}
	}
	
	/**
	 * 
	 * Join workspace implementation - first stage: Request to join, to get a session ID
	 * 
	 * @param wspURI workspace URI
	 * @param cred agent credentials
	 * @return session id
	 * @throws CartagoException
	 */
	synchronized String requestToJoinWorkspace(URI wspURI, AgentCredential cred) throws cartago.security.SecurityException, CartagoException{
		try {
			String sid  = this.infraLayer.requestToJoinWorkspace(wspURI, cred);
			return sid;
		} catch (CartagoInfrastructureLayerException ex) {
			ex.printStackTrace();
			throw new CartagoException("Join " + wspURI + " failed ");
		}
	}

	/**
	 * 
	 * Join workspace implementation - second stage: Join with a session ID
	 * 
	 * @param wspId workspace identifier
	 * @param sid session id
	 * @param eventListener listener to receive CArtAgO events
	 * @return
	 * @throws CartagoException
	 */
	synchronized ICartagoContext completeJoinWorkspace(WorkspaceId wspId, String sid, ICartagoCallback eventListener) throws cartago.security.SecurityException, CartagoException{
		try {
			ICartagoContext ctx = this.infraLayer.joinWorkspace(wspId, sid, eventListener);
			return ctx;
		} catch (CartagoInfrastructureLayerException ex) {
			ex.printStackTrace();
			throw new CartagoException("Join " + wspId + " failed ");
		}
	}

		
	/* factory management */
	
	/**
	 * Add an artifact factory for artifact templates
	 * 
	 * @param wspName workspace name
	 * @param factory artifact factory
	 * @throws CartagoException
	 */
	public synchronized void addArtifactFactory(String wspName, ArtifactFactory factory) throws CartagoException {
		wsp.addArtifactFactory(factory);
	}
	
	/**
	 * Remove an existing class loader for artifacts
	 * @param wspName workspace name
	 * @param name id of the artifact factory
	 * @throws CartagoException
	 */
	public synchronized void removeArtifactFactory(String wspName, String name) throws CartagoException {
		wsp.removeArtifactFactory(name);
	}

	/**
	 * Register a new logger for CArtAgO Workspace Kernel events
	 * 
	 * @param wspName
	 * @param logger
	 * @throws CartagoException
	 */
	public synchronized void registerLogger(String wspName, ICartagoLogger logger) throws CartagoException {
		wsp.registerLogger(logger);
	}

	/* debugging */
	
	/**
	 * Enable debugging of a CArtAgO Workspace 
	 * 
	 * @param wspName
	 * @throws CartagoException
	 */
	public synchronized void enableDebug(String wspName) throws CartagoException {
		Inspector insp = debuggers.get(wspName);
		 if (insp == null){
			insp = new Inspector();
			insp.start();
			registerLogger(wspName, insp.getLogger());
			debuggers.put(wspName, insp);
		}
	}
	
	/**
	 * Disable debugging of a CArtAgO Workspace 
	 * 
	 * @param wspName
	 * @throws CartagoException
	 */
	public synchronized void disableDebug(String wspName) throws CartagoException {
			 Inspector insp = debuggers.remove(wspName);
			 if (insp != null){
				 wsp.unregisterLogger(insp.getLogger());
			 }
	}
	
	
	/* loggers */
	
	/**
	 * 
	 * Unregister a logger 
	 * 
	 * @param wspName
	 * @param logger
	 * @throws CartagoException
	 */
	public synchronized void unregisterLogger(String wspName, ICartagoLogger logger) throws CartagoException {
		wsp.unregisterLogger(logger);
	}	
	
	/**
	 * Getting a controller.
	 * 
	 * @param wspName
	 * @return
	 * @throws CartagoException
	 */
	public synchronized ICartagoController  getController(String wspName) throws CartagoException {
		return wsp.getController();
	}
	
	/**
	 * 
	 * Main to create a workspace, used by createWorkspace
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		try {
			System.out.println(SPLASH_MSG);
			
			CartagoDistributedEnvironment env = CartagoDistributedEnvironment.getInstance();
			String wspName = CartagoDistributedEnvironment.WSP_DEFAULT_NAME;
			int wspPort = cartago.infrastructure.CartagoInfrastructureLayerImpl.WSP_DEFAULT_PORT;
			
			if (hasOption(args, "-name")) {
				wspName = getParam(args, "-name");
			}
			
			if (hasOption(args, "-port")) {
				wspPort = Integer.parseInt(getParam(args, "-port"));
			}
			env.initWsp(wspName, wspPort, Optional.empty());
						
			System.out.println(READY_MSG);
		
		} catch (Exception ex){
			System.err.println("Execution Failed: "+ex.getMessage());
		}
	}
	
	
	private static boolean hasOption(String[] args, String arg){
		for (int i = 0; i<args.length; i++){
			if (args[i].equals(arg) && i<args.length-1){
				return true;
			} 
		}
		return false;
	}

	private static String getParam(String[] args, String arg){
		for (int i = 0; i<args.length; i++){
			if (args[i].equals(arg) && i<args.length-1){
				return args[i+1];
			} 
		}
		return null;
	}
}
