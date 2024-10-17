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

import java.net.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import cartago.*;
import cartago.security.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;


/**
 * CArtAgO RMI Infrastructure Service - enables remote interaction exploiting RMI transport protocol.
 *  
 * @author aricci
 *
 */
public class CartagoInfrastructureLayerImpl implements CartagoInfrastructureLayer {
	
	static public final int WSP_DEFAULT_PORT = 20100; 
	
	private WorkspaceService service;
	private Vertx vertx;
	private boolean error = false;
	
	
	/**
	 * Infra layer for infrastructure wsp.
	 * 
	 * @param wspId
	 * @throws CartagoInfrastructureLayerException
	 */
	protected CartagoInfrastructureLayerImpl(URI wspId) throws CartagoInfrastructureLayerException {
		vertx = Vertx.vertx();
		try {
			service = new WorkspaceService();
			service.deploy(wspId);
		} catch (Exception ex){
			ex.printStackTrace();
			throw new CartagoInfrastructureLayerException();
		}
		var t0 = System.currentTimeMillis();
		while (!service.isReady() && !service.isFailed() && (System.currentTimeMillis() - t0 < 10000)) {
			try {
				Thread.sleep(100);
			} catch (Exception ex) {}
		}
		if (!service.isReady()) {
			throw new CartagoInfrastructureLayerException();
		}
	}

	/**
	 * 
	 *  Infra layer for standalone wsp.
	 * 
	 */
	protected CartagoInfrastructureLayerImpl() {
		vertx = Vertx.vertx();
		
	}
	
	public void shutdownLayer() throws CartagoException {
		if (service != null){
			service.shutdownService();
			service = null;
		}
	}
	
	public ICartagoContext joinWorkspace(WorkspaceId wspId, String agentSessionId, ICartagoCallback eventListener) throws CartagoInfrastructureLayerException, CartagoException {
		
		try {
			
			InfrastructureWspId wid = (InfrastructureWspId) wspId;
			URI uri = wid.getURI();
			String host = uri.getHost();
			int port = uri.getPort();
			if (port == -1) {
				port = WSP_DEFAULT_PORT;
			}

			HttpClientOptions options = new HttpClientOptions().setDefaultHost(host).setDefaultPort(port);
			HttpClient client = vertx.createHttpClient(options);
			
			AgentBodyProxy proxy = new AgentBodyProxy(vertx, port);
			
			Semaphore ev = new Semaphore(0);
			error = false;
			
			client.websocket("/cartago/api/join", (WebSocket ws) -> {
				  
				 
				  JsonObject params = new JsonObject();
				  params.put("session-id", agentSessionId);

				  ws.handler((Buffer b) -> {
					  try {
						proxy.init(ws, wspId, eventListener);
					  } catch (Exception ex) { 
						ex.printStackTrace();  
					  } finally {
					  	ev.release();
					  }
				  });
				  
				  ws.writeTextMessage(params.encode());

				   
			}, err -> {
				  System.out.println("Error!");
				  error = true;
				  ev.release();
			
			});

			ev.acquire();
			if (!error) {
				return proxy;
			} else {
				throw new CartagoInfrastructureLayerException();
			}
			
		} catch (Exception ex){
			ex.printStackTrace();
			throw new CartagoInfrastructureLayerException();
		}
		
	}
	
	public String requestToJoinWorkspace(URI wspId, AgentCredential cred) throws CartagoInfrastructureLayerException, CartagoException {
		
		try {
			
			String host = wspId.getHost();
			int port = wspId.getPort();
			if (port == -1) {
				port = WSP_DEFAULT_PORT;
			}

			HttpClientOptions options = new HttpClientOptions().setDefaultHost(host).setDefaultPort(port);
			HttpClient client = vertx.createHttpClient(options);
			
			// AgentBodyProxy proxy = new AgentBodyProxy(vertx, port);
			
			Semaphore ev = new Semaphore(0);
			error = false;
			StringBuffer sessionId = new StringBuffer();
			
			client.websocket("/cartago/api/request-to-join", (WebSocket ws) -> {
				  JsonObject params = new JsonObject();
				  // params.put("wspFullName", wspFullNameRemote);
				  JsonObject ac = new JsonObject();
				  ac.put("userName", cred.getId());
				  ac.put("roleName", cred.getRoleName());				  
				  params.put("agent-cred", ac);
				  
				  log("New request to join from: " + cred.getId());

				  ws.handler((Buffer b) -> {
					  try {
							JsonObject reply = b.toJsonObject();

							String agentSessionId = reply.getString("session-id");
							// System.out.println("AGENT SESSION ID: " + reply);			
							sessionId.append(agentSessionId);
							log("Request from " + cred.getId() + " accepted - session id: " + agentSessionId);

						// WorkspaceId id = WorkspaceId.makeInfrastructureWspIJd(wspId);
						// proxy.init(ws, id, eventListener);
					  } catch (Exception ex) { 
						ex.printStackTrace();  
					  } finally {
					  	ev.release();
					  }
				  });
				
				  ws.writeTextMessage(params.encode());

			}, err -> {
				  System.out.println("Error!");
				  error = true;
				  ev.release();
			
			});

			ev.acquire();
			if (!error) {
				return sessionId.toString();
			} else {
		
				throw new CartagoInfrastructureLayerException();
			}
			
		} catch (Exception ex){
			ex.printStackTrace();
			throw new CartagoInfrastructureLayerException();
		}
		
	}

	
	public OpId execRemoteInterArtifactOp(ICartagoCallback callback, long callbackId,
			AgentId userId, ArtifactId srcId, ArtifactId targetId, String address, Op op,
			long timeout, IAlignmentTest test)
			throws CartagoInfrastructureLayerException, CartagoException {
		/* try {
			CartagoCallbackRemote srv = new CartagoCallbackRemote(callback);
			CartagoCallbackProxy proxy = new CartagoCallbackProxy(srv);
			String fullAddress = address;
			if (getPort(address)==-1){
				fullAddress = address+":"+DEFAULT_PORT;
			}
			ICartagoNodeRemote env = (ICartagoNodeRemote)Naming.lookup("rmi://"+fullAddress+"/cartago_node");
			return env.execInterArtifactOp(proxy, callbackId, userId, srcId, targetId, op, timeout, test);
		} catch (Exception ex){
			ex.printStackTrace();
			throw new CartagoException("Inter-artifact op failed: "+ex.getLocalizedMessage());
		}*/
		throw new RuntimeException("not implemented");
	}


	private void log(String msg) {
		System.out.println("[CartagoInfraLayer] " + msg);
	}

	
	//
	


}
