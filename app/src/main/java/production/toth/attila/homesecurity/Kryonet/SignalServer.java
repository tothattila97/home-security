package production.toth.attila.homesecurity.Kryonet;

import android.util.Log;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.rmi.ObjectSpace;
import com.esotericsoftware.kryonet.rmi.RemoteObject;

import java.io.IOException;

import production.toth.attila.homesecurity.ImageConsumer;


public class SignalServer {

    private Server server;

    public SignalServer() throws IOException{
        server = new Server(){
           protected Connection newConnection(){
               return new SignalConnection();
           }
        };

        Network.register(server);

        server.addListener(new Listener() {
            public void received (Connection c, Object object) {
                SignalConnection connection = (SignalConnection) c;

                if(object instanceof Signal){

                    /*Network.Signal signal = (Network.Signal)object;
                    String sign = signal.sign;
                    if(sign!=null){
                        sign = sign.trim();

                    }
                    Log.e("homesecurity", sign);*/

                    //Remote Method Invocation
                    if(((Signal) object).sign.equals("Something happened")){
                        ObjectSpace.registerClasses(server.getKryo());
                        ObjectSpace objectSpace = new ObjectSpace();
                        // ImageConsumer osztályt kéne átadni nem a Callback interfacet de valamiért interfacet vár
                        ImageConsumer.IRingtoneCallback someObject = ObjectSpace.getRemoteObject(connection, 42, ImageConsumer.IRingtoneCallback.class);
                        ((RemoteObject)someObject).setNonBlocking(true);
                        objectSpace.register(42, someObject);
                        someObject.playRingtone();
                        objectSpace.addConnection(connection);

                        //ImageConsumer.getInstance().callback.playRingtone();

                    }
                }
            }

            public void disconnected (Connection c) {
                SignalConnection connection = (SignalConnection)c;
                if (connection.name != null) {
                    // Announce to everyone that someone (with a registered name) has left.
                    /*ChatMessage chatMessage = new ChatMessage();
                    chatMessage.text = connection.name + " disconnected.";
                    server.sendToAllTCP(chatMessage);
                    updateNames();*/
                    Log.e("Homesecurity", "Kryonet SignalServer disconnected");
                }
            }
        });

        server.bind(Network.portTCP, Network.portUDP);
        server.start();

    }



    public class SignalConnection extends Connection {
        public String name;
        SignalConnection(){}
        SignalConnection(String n){this.name=n;}
    }
}
