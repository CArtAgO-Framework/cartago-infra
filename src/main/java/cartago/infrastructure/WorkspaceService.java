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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import cartago.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;


/**
 * Class representing a CArtAgO node service, serving remote requests
 *  
 * @author aricci
 *
 */
public class WorkspaceService extends AbstractVerticle  {

	private String fullAddress;
	private int port;
		
	private Router router;
	private HttpServer server;
		
	private Logger logger = LoggerFactory.getLogger(WorkspaceService.class);
	
	private static final String API_BASE_PATH = "/cartago/api";

	private ConcurrentHashMap<String, AgentBodyRemote> remoteCtxs;
	// private GarbageBodyCollectorAgent garbageCollector;
	private ConcurrentHashMap<String, AgentBody> pendingBodies;
	private AtomicLong agentSessionId;

	private AtomicBoolean isReady;
	private AtomicBoolean initFailed;
	
	
	public WorkspaceService() throws Exception {
		remoteCtxs = new ConcurrentHashMap<String, AgentBodyRemote>();	
		// garbageCollector = new GarbageBodyCollectorAgent(remoteCtxs,1000,10000);
		// garbageCollector.start();
		pendingBodies = new  ConcurrentHashMap<String, AgentBody>();
		agentSessionId = new AtomicLong(0);
		isReady = new AtomicBoolean(false);
		initFailed = new AtomicBoolean(false);
	}	
		
	public void deploy(URI wsp) throws CartagoInfrastructureLayerException {
		
		/* WARNING: the  timeout - 1000 - must be greater than the 
		   delay used by the KeepRemoteContextAliveManager
		   to keep alive the remote contexts */
		//
		this.port = wsp.getPort();
		
		Vertx vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(10000));
		vertx.deployVerticle(this);
	}	

	 
	
	@Override
	public void start() {
		
		router = Router.router(vertx);
		
		router.route().handler(CorsHandler.create("*")
				.allowedMethod(io.vertx.core.http.HttpMethod.GET)
				.allowedMethod(io.vertx.core.http.HttpMethod.POST)
				.allowedMethod(io.vertx.core.http.HttpMethod.PUT)
				.allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
				.allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
				.allowedHeader("Access-Control-Request-Method")
				.allowedHeader("Access-Control-Allow-Credentials")
				.allowedHeader("Access-Control-Allow-Origin")
				.allowedHeader("Access-Control-Allow-Headers")
				.allowedHeader("Content-Type"));

		router.route().handler(BodyHandler.create());
		
		router.get(API_BASE_PATH + "/version").handler(this::handleGetVersion);
		 
		server = vertx.createHttpServer()
		.requestHandler(router)
		.websocketHandler(ws -> {
			 if (ws.path().equals(API_BASE_PATH + "/request-to-join")) {
				 this.handleRequestToJoinWSP(ws);
			 } else if (ws.path().equals(API_BASE_PATH + "/join")) {
				 this.handleJoinWSP(ws);
			 }  else {
				 ws.reject();
			 }
		})
		.listen(port, result -> {
			if (result.succeeded()) {
				/* the port could have been defined dynamically */
				
				if (port == 0) {
					port = server.actualPort();
					var wspId = (InfrastructureWspId) CartagoDistributedEnvironment.getInstance().getWorkspace().getId();
					URI newURI = URI.create("http://" + wspId.getURI().getHost() + ":" + port + "/" + wspId.getURI().getPath());
					wspId.finalizeURI(newURI);
				}
				log("Ready.");
				isReady.set(true);
			} else {
				log("Failed: "+result.cause());
				initFailed.set(true);
			}
		});
	}

	public boolean isReady() {
		return isReady.get();
	}

	public boolean isFailed() {
		return initFailed.get();
	}
	
	
	private  void log(String msg) {
		System.out.println("[ Workspace " + CartagoDistributedEnvironment.getInstance().getWorkspace().getId() + " ] " + msg);
	}

	public void shutdownService(){
		// garbageCollector.stopActivity();		
		server.close();		
	}


	/* JOIN */
	
	private void handleRequestToJoinWSP(ServerWebSocket ws) {
		log("Handling Request to Join WSP from "+ws.remoteAddress() + " - " + ws.path());
		WorkspaceService service = this;
		
		ws.handler(buffer -> {
				JsonObject joinParams = buffer.toJsonObject();
		
				// String wspName = joinParams.getString("wspFullName");
				JsonObject agentCred = joinParams.getJsonObject("agent-cred");
				
				String userName = agentCred.getString("userName");
				String roleName = agentCred.getString("roleName");
		
				AgentCredential cred = 	new AgentIdCredential(userName, roleName);
						
				try {
					Workspace wsp = CartagoDistributedEnvironment.getInstance().getWorkspace();			
					log("Remote request to join: " + roleName + " " + cred);
						
				    AgentBodyRemote rbody = new AgentBodyRemote();			
					ICartagoContext ctx = wsp.joinWorkspace(cred, rbody);
					
					long value = agentSessionId.incrementAndGet();
					String agentSessionId = "sid-" + value;
					
					remoteCtxs.put(agentSessionId, rbody);
		
					rbody.init((AgentBody) ctx);	
					
					JsonObject res = new JsonObject();
					res.put("session-id", agentSessionId);
					
					ws.writeTextMessage(res.encode());
					
					
				} catch (Exception ex) {
					ex.printStackTrace();
					ws.reject();
				}
			});
	}
	
	private void handleJoinWSP(ServerWebSocket ws) {
		log("Handling Join WSP from "+ws.remoteAddress() + " - " + ws.path());
		WorkspaceService service = this;
		
		ws.handler(buffer -> {
				JsonObject joinParams = buffer.toJsonObject();
		
				String agentSessionId = joinParams.getString("session-id");
				
				AgentBodyRemote rbody = remoteCtxs.get(agentSessionId);
				
				if (rbody != null) {
			
					rbody.connect(ws, service);	
					
					JsonObject reply = new JsonObject();
					reply.put("state", "connected");
					ws.writeTextMessage(reply.encode());
					
				} else {
					ws.reject();
				}
			});
	}	
	
	public void registerNewJoin(String bodyId, AgentBody body) {
		this.pendingBodies.put(bodyId, body);
	}
	
	/* QUIT */
	
	private void handleQuitWSP(RoutingContext routingContext) {
		log("Handling Quit WSP from "+routingContext.request().absoluteURI());
		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "application/text").end("Not implemented.");
	}
	
	/*
	public void quit(String wspName, AgentId id) throws RemoteException, CartagoException {
		CartagoWorkspace wsp = node.getWorkspace(wspName);		
		wsp.quitAgent(id);
		Iterator<AgentBodyRemote> it = remoteCtxs.iterator();
		while (it.hasNext()){
			AgentBodyRemote c = it.next();
			if (c.getAgentId().equals(id)){
				it.remove();
				break;
			}
		} 
	}*/
	
	
	/* exec Inter-artifact  */
	
	private void handleExecIAOP(RoutingContext routingContext) {
		log("Handling Exec Inter artifact OP from "+routingContext.request().absoluteURI());
		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "application/text").end("Not implemented.");
	}
	/**
	 * Exec an inter-artifact operation call.
	 * 
	 * @param callback
	 * @param callbackId
	 * @param agentSessionId
	 * @param srcId
	 * @param targetId
	 * @param op
	 * @param timeout
	 * @param test
	 * @return
	 * @throws RemoteException
	 * @throws CartagoException
	 *//*
	public OpId execInterArtifactOp(ICartagoCallback callback, long callbackId, AgentId userId, ArtifactId srcId, ArtifactId targetId, Op op, long timeout, IAlignmentTest test) throws RemoteException, CartagoException {
		String wspName = targetId.getWorkspaceId().getName();
		CartagoWorkspace wsp = (CartagoWorkspace) node.getWorkspace(wspName);
		return wsp.execInterArtifactOp(callback, callbackId, userId, srcId, targetId, op, timeout, test);
	}*/	 
	

	/* GET VERSION */

	private void handleGetVersion(RoutingContext routingContext) {
		log("Handling Get Version from "+routingContext.request().absoluteURI());
		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "application/text").end(CARTAGO_VERSION.getID());
	}

	/* GET WORKSPACE INFO */
/*
	private void handleResolveWSP(RoutingContext routingContext) {
		log("Handling ResolveWSP from "+routingContext.request().absoluteURI());
		String envName = routingContext.request().getParam("masName");
		String fullPath = routingContext.request().getParam("wsp");
		JsonObject obj = new JsonObject();
		try {
			WorkspaceDescriptor des = CartagoEnvironment.getInstance().resolveWSP(fullPath);
			obj.put("envName", des.getEnvName());
			obj.put("envId", des.getEnvId().toString());
			if (des.isLocal()) {
				obj.put("id", JsonUtil.toJson(des.getId()));
			} else {
				obj.put("remotePath", des.getRemotePath());
				obj.put("address", des.getAddress());
				obj.put("protocol", des.getProtocol());
			}
			routingContext.response().putHeader("content-type", "application/text").end(obj.encode());
		} catch (Exception ex) {
			HttpServerResponse response = routingContext.response();
			response.setStatusCode(404).end();
		}
	}
*/

}
