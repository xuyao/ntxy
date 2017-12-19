package cn.net.xy.zb;

import org.apache.log4j.Logger;

import websocketx.client.RunExample;
import websocketx.client.WebSocketClient;



public class App {
	
	private static Logger log = Logger.getLogger(App.class);
	
	public static WebSocketClient client;
	
	public static void main( String[] args ){

		try {
			if(client==null){
				log.info("链接到"+WebSocketClient.serverUrl);
				client = new WebSocketClient( WebSocketClient.serverUrl );
			}
			client.connect();
			log.info("================================"+client.isAlive());
			client.doTask(client);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
