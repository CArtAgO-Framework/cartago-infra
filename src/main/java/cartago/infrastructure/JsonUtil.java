package cartago.infrastructure;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactObsProperty;
import cartago.CartagoException;
import cartago.Op;
import cartago.OpFeedbackParam;
import cartago.Tuple;
import cartago.WorkspaceId;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonUtil {

    static private ObjectMapper objectMapper;
    static private SimpleModule module;
	static {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // module = new SimpleModule("cartago-module", new Version(1, 0, 0, null, null, null));
        // module.addSerializer(OpFeedbackParam.class, new OpFeedbackParamSerializer());
        // module.addDeserializer(OpFeedbackParam.class, new OpFeedbackParamDeserializer());
        // objectMapper.registerModule(module);
		
	}
	
	// ---

	static public JsonObject toJson(Op op) {
		JsonArray params = new JsonArray();
		for (Object p: op.getParamValues()) {
			if (p != null) {
				JsonObject param = new JsonObject();
				String paramClass = p.getClass().getName();
				param.put("paramClass", paramClass);
				if (!paramClass.equals("cartago.OpFeedbackParam")) {
					if (paramClass.equals("cartago.ArtifactId")) {
						JsonObject json = toJson((ArtifactId) p);
						param.put("paramValue", json);
						
					} else if (paramClass.equals("cartago.infrastructure.InfrastructureWspId")) {
						JsonObject json = toJson((WorkspaceId) p);
						param.put("paramValue", json);
					} else{
						try {
							String ser = objectMapper.writeValueAsString(p);
							param.put("paramValue", ser);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				} else {
					OpFeedbackParam opp = (OpFeedbackParam) p;
					Object val = opp.get();
					if (val != null) {
						JsonObject varx = new JsonObject();
						String pparamClass = val.getClass().getName();
						varx.put("class", pparamClass);
						if (pparamClass.equals("cartago.ArtifactId")) {
							JsonObject json = toJson((ArtifactId) val);
							varx.put("value", json);
						} else if (pparamClass.equals("cartago.infrastructure.InfrastructureWspId")) {
							JsonObject json = toJson((WorkspaceId) val);
							varx.put("value", json);
						} else {
							try {
								String ser = objectMapper.writeValueAsString(val);
								varx.put("value", ser);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
						param.put("paramValue", varx);
					}
				}
				params.add(param);
			} else {
				System.out.println("null value");
			}
		}
		
		JsonObject opInfo = new JsonObject();
		opInfo.put("name", op.getName());
		opInfo.put("params", params);
		return opInfo;
	}
	
	static public Op toOp(JsonObject obj) {
		String opName = obj.getString("name");
		JsonArray params = obj.getJsonArray("params");
		Object[] par = new Object[params.size()];
		for (int i = 0; i < params.size(); i++) {
			JsonObject param = params.getJsonObject(i);
			String paramClassName = param.getString("paramClass");
			if (!paramClassName.equals("cartago.OpFeedbackParam")) {
				try {
					Object val = null;
					if (paramClassName.equals("cartago.ArtifactId")) {
						JsonObject pvalue = param.getJsonObject("paramValue");	      			  
						// JsonObject value = pvalue.getJsonObject("value");
						val = toArtifactId(pvalue);
					} else if (paramClassName.equals("cartago.infrastructure.InfrastructureWspId")) {
						JsonObject pvalue = param.getJsonObject("paramValue");	      			  
						// JsonObject value = pvalue.getJsonObject("value");
						val = toWorkspaceId(pvalue);
					} else{
						String value = param.getString("paramValue");
						if (value != null) {
							val = objectMapper.readValue(value, Class.forName(paramClassName));
						}
					}
					par[i] = val;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
					OpFeedbackParam opp = new OpFeedbackParam();
					try {
						JsonObject pvalue = param.getJsonObject("paramValue");	      			  
						if (pvalue != null) {
							String paramClass = pvalue.getString("class");
							Object val = null;
							if (paramClass.equals("cartago.ArtifactId")) {
								JsonObject value = pvalue.getJsonObject("value");
								val = toArtifactId(value);
							} else if (paramClass.equals("cartago.infrastructure.InfrastructureWspId")) {
								JsonObject value = pvalue.getJsonObject("value");
								val = toWorkspaceId(value);
							} else{
								String paramValue = pvalue.getString("value");
								val = objectMapper.readValue(paramValue, Class.forName(paramClass));
							}
							opp.set(val);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					par[i] = opp;
				}
		}			    			
		return new Op(opName, par);
	}

	// ---
	
	static public  JsonObject toJson(AgentId id) {
		JsonObject obj = new JsonObject();
		obj.put("localId", id.getLocalId());
		obj.put("agentName", id.getAgentName());
		obj.put("agentRole", id.getAgentRole());
		JsonObject wid = toJson(id.getWorkspaceId());
		obj.put("workspaceId", wid);
		obj.put("globalId", id.getGlobalId());		
		return obj;
	}
	
	static public AgentId toAgentId(JsonObject obj) throws CartagoException {
		String agentName = obj.getString("agentName");
		String agentRole = obj.getString("agentRole");
		int localId = obj.getInteger("localId");
		String globalId = obj.getString("globalId");
		WorkspaceId wid = toWorkspaceId(obj.getJsonObject("workspaceId"));
		return new AgentId(agentName, globalId, localId, agentRole, wid);
	}
	
	// --
	
	static public JsonObject toJson(WorkspaceId id) {
		JsonObject obj = new JsonObject();
		obj.put("fullName", id.getName());
		return obj;
	}

	static public WorkspaceId toWorkspaceId(JsonObject obj) throws CartagoException {		
		String fullName = obj.getString("fullName");
		return new InfrastructureWspId(URI.create(fullName));
	}
	
	// --
	
	// 	public ArtifactId(String name, UUID id, String artifactType, WorkspaceId wspId, AgentId creatorId){

	static public  JsonObject toJson(ArtifactId id) {
		JsonObject obj = new JsonObject();
		// obj.put("id", id.getId().toString());
		obj.put("name", id.getName());
		obj.put("artifactType", id.getArtifactType());
		JsonObject wid = toJson(id.getWorkspaceId());
		obj.put("workspaceId", wid);
		JsonObject cid = toJson(id.getCreatorId());
		obj.put("creatorId", cid);		
		return obj;
	}
	
	static public ArtifactId toArtifactId(JsonObject obj) throws CartagoException {
		if (obj != null) {
			String name = obj.getString("name");
			// String id = obj.getString("id");
			String artifactType = obj.getString("artifactType");
			var jsonWsp = obj.getJsonObject("workspaceId");
			WorkspaceId workspaceId = null;
			if (jsonWsp != null) {
				workspaceId = toWorkspaceId(jsonWsp);
			} else {
				System.err.println("null wspId..");
			}
			AgentId creatorId = toAgentId(obj.getJsonObject("creatorId"));
			return new ArtifactId(name, artifactType, workspaceId, creatorId);
		} else {
			return null;
		}
	}
	
	// --

	static public  JsonObject toJson(Tuple t) {
		JsonObject obj = new JsonObject();
		obj.put("name", t.getLabel());
		JsonArray params = new JsonArray();
		for (Object p: t.getContents()) {
			if (p != null) {
				JsonObject param = new JsonObject();
				param.put("paramClass", p.getClass().getName());
				try {
					String ser = objectMapper.writeValueAsString(p);
					param.put("paramValue", ser);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				params.add(param);
			}
		}
		obj.put("params", params);
		return obj;
	}
	
	
	static public Tuple toTuple(JsonObject obj) {
		if (obj != null) {
			String name = obj.getString("name");
			JsonArray params = obj.getJsonArray("params");
			Object[] par = new Object[params.size()];
			for (int i = 0; i < params.size(); i++) {
				JsonObject param = params.getJsonObject(i);
				String paramClassName = param.getString("paramClass");
				String value = param.getString("paramValue");	      			  
				try {
					if (value != null) {
						par[i] = objectMapper.readValue(value, Class.forName(paramClassName));
					} 
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} 			    			
			return new Tuple(name, par);
		} else {
			return null;
		}
	}
	
	// --
	
	static public  JsonObject toJson(ArtifactObsProperty prop) {
		JsonObject obj = new JsonObject();
		obj.put("name", prop.getName());
		obj.put("id", prop.getId());
		obj.put("fullId", prop.getFullId());		
		JsonArray params = new JsonArray();
		for (Object p: prop.getValues()) {
			JsonObject param = new JsonObject();
			String paramClass = p.getClass().getName();
			param.put("paramClass", paramClass);
			if (paramClass.equals("cartago.ArtifactId")) {
				JsonObject json = toJson((ArtifactId) p);
				param.put("paramValue", json);	
			} else if (paramClass.equals("cartago.infrastructure.InfrastructureWspId")) {
				JsonObject json = toJson((WorkspaceId) p);
				param.put("paramValue", json);
			} else{
				try {
					String ser = objectMapper.writeValueAsString(p);
					param.put("paramValue", ser);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			params.add(param);
		}
		obj.put("params", params);
		return obj;
	}
	
	static public ArtifactObsProperty toArtifactObsProperty(JsonObject obj) {
		String name = obj.getString("name");
		long id = obj.getLong("id");
		String fullId = obj.getString("fullId");
		JsonArray params = obj.getJsonArray("params");
		Object[] par = new Object[params.size()];
		for (int i = 0; i < params.size(); i++) {
			JsonObject param = params.getJsonObject(i);
			String paramClassName = param.getString("paramClass");
		
			try {
				Object val = null;
				if (paramClassName.equals("cartago.ArtifactId")) {
					JsonObject pvalue = param.getJsonObject("paramValue");	      			  
					// JsonObject value = pvalue.getJsonObject("value");
					val = toArtifactId(pvalue);
				} else if (paramClassName.equals("cartago.infrastructure.InfrastructureWspId")) {
					JsonObject pvalue = param.getJsonObject("paramValue");	      			  
					// JsonObject value = pvalue.getJsonObject("value");
					val = toWorkspaceId(pvalue);
				} else{
					String value = param.getString("paramValue");
					if (value != null) {
						val = objectMapper.readValue(value, Class.forName(paramClassName));
					}
				}
				par[i] = val;
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			
			/*
		
			String value = param.getString("paramValue");	      			  
			try {
				if (value != null) {
					par[i] = objectMapper.readValue(value, Class.forName(paramClassName));
				} 
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			
			*/
		
		}
			
			
				
		return new ArtifactObsProperty(fullId, id, name, par);
	}

	// --
	
	static public  JsonArray toJson(ArtifactObsProperty[] props) {
		JsonArray obj = new JsonArray();
		for (ArtifactObsProperty prop: props) {
			obj.add(toJson(prop));
		}
		return obj;
	}
		
	static public ArtifactObsProperty[] toArtifactObsPropertyArray(JsonArray obj) {
		if (obj != null) {
			ArtifactObsProperty[] elems = new ArtifactObsProperty[obj.size()];
			for (int i = 0; i < elems.length; i++) {
				elems[i] = toArtifactObsProperty(obj.getJsonObject(i));
			}
			return elems;
		} else {
			return null;
		}
	}
	
	// 
	
	
	static public  JsonArray toJson(Collection<ArtifactObsProperty> props) {
		JsonArray obj = new JsonArray();
		for (ArtifactObsProperty prop: props) {
			obj.add(toJson(prop));
		}
		return obj;
	}

	static public List<ArtifactObsProperty> toArtifactObsPropertyList(JsonArray obj) {
		if (obj != null) {
			List<ArtifactObsProperty> elems = new ArrayList<ArtifactObsProperty>();
			for (int i = 0; i < obj.size(); i++) {
				elems.add(toArtifactObsProperty(obj.getJsonObject(i)));
			}
			return elems;
		} else {
			return null;
		}
	}
	
	//
	/*
	static public  JsonObject toJson(GlobalWorkspaceInfo info) {
		JsonObject obj = new JsonObject();
		obj.put("envName", info.getEnvName());
		obj.put("envId", info.getEnvId().toString());
		obj.put("fullName", info.getFullName());
		obj.put("address", info.getAddress());
		obj.put("protocol", info.getProtocol());
		JsonArray array = new JsonArray();
		for (Entry<String, GlobalWorkspaceInfo> elem: info.getLinkedWsps().entrySet()) {
			JsonObject entry = new JsonObject();
			entry.put("link", elem.getKey());
			JsonObject wsp = new JsonObject();
			wsp.put("envName", elem.getValue().getEnvName());
			wsp.put("envId", elem.getValue().getEnvId().toString());
			wsp.put("fullName", elem.getValue().getFullName());
			wsp.put("address", elem.getValue().getAddress());
			wsp.put("protocol", elem.getValue().getProtocol());
			entry.put("wspInfo", wsp);
			array.add(entry);
		}
		obj.put("linked", array);
		return obj;
	}

	static public GlobalWorkspaceInfo toGlobalWorkspaceInfo(JsonObject obj) throws CartagoException {
		if (obj != null) {
			String envName = obj.getString("envName");
			String envId = obj.getString("envId");
			String fullName = obj.getString("fullName");
			String address = obj.getString("address");
			String protocol = obj.getString("protocol");
			HashMap<String, GlobalWorkspaceInfo> map = new HashMap<>();
			JsonArray entries = obj.getJsonArray("linked");
			for (int i = 0; i < entries.size(); i++) {
				JsonObject entry = entries.getJsonObject(i);
				
				String link = entry.getString("link");
				JsonObject wsp = entry.getJsonObject("wspInfo");
				
				String envName2 = wsp.getString("envName");
				String envId2 = wsp.getString("envId");
				String fullName2 = wsp.getString("fullName");
				String address2 = wsp.getString("address");
				String protocol2 = wsp.getString("protocol");

				GlobalWorkspaceInfo winfo = new GlobalWorkspaceInfo(envName2, UUID.fromString(envId2), fullName2, address2, protocol2);
				map.put(link, winfo);
			}
			return new GlobalWorkspaceInfo(envName, UUID.fromString(envId), fullName, address, protocol, map);
		} else {
			return null;
		}
	}
		*/
}
