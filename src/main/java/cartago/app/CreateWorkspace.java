package cartago.app;

import java.net.URI;
import java.util.Optional;

import cartago.infrastructure.CartagoDistributedEnvironment;

public class CreateWorkspace {

    public static void main(String[] args) throws Exception {
        CartagoDistributedEnvironment env = CartagoDistributedEnvironment.getInstance();
    	
        String name = CartagoDistributedEnvironment.WSP_DEFAULT_NAME;
        if (hasOption(args, "-name")) {
        	name = getParam(args, "-name");
        }
        
        int port = cartago.infrastructure.CartagoInfrastructureLayerImpl.WSP_DEFAULT_PORT;
        if (hasOption(args, "-port")) {
        	try {
        		String portVal = getParam(args, "-port");
        		if (portVal.equals("any")) {
        			port = 0;
        		} else {
        			port = Integer.parseInt(portVal);
        		}
        	} catch (Exception ex) {
        		System.err.println("Invalid port number.");
        		System.exit(1);
        	}
        }
        
		try {
			URI uri = URI.create("http://localhost:" + port + "/" + name);
			if (port != 0) {
				System.out.println("Launching workspace " + uri + " ...");
			} else {
				System.out.println("Launching workspace " + name + " on localhost, the port will be determined automatically...");
			}
			env.initWsp(name, port, Optional.empty());;
  		} catch (Exception ex) {
  			System.err.println("Port not available.");
  			System.exit(2);
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