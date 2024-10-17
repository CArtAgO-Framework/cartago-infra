package cartago.infrastructure;

import java.util.Optional;

public class WorkspaceDescriptor {

	private InfrastructureWspId wspId;
	private String localRef;
	private Optional<Process> wspProcess;
	private boolean isChild;

	
	public WorkspaceDescriptor(InfrastructureWspId wspId, String localRef) {
		this.wspId = wspId;
		this.localRef = localRef;
		this.isChild = false;
		wspProcess = Optional.empty();
	}
	
	public WorkspaceDescriptor(InfrastructureWspId wspId, String localRef, Process wspProc) {
		this.wspId = wspId;
		this.isChild = true;
		wspProcess = Optional.of(wspProc);
		this.localRef = localRef;
	}
	
	public boolean isChild() {
		return isChild;
	}
	
	public Process getWspProcess() {
		return wspProcess.get();
	}

	public String getLocalRef() {
		return localRef;
	}
	
	public InfrastructureWspId getWorkspaceId() {
		return this.wspId;
	}

}
