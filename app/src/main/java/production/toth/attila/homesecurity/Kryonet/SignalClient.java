package production.toth.attila.homesecurity.Kryonet;

import com.esotericsoftware.kryonet.Client;

import java.net.InetAddress;


public class SignalClient implements Runnable {

    private Client client;

    /*public SignalClient() throws IOException{
        client = new Client();

        Network.register(client);
        //client.addListener();
        InetAddress address = client.discoverHost(54777, 5000);
        //client.start();
        client.connect(5000,address,Network.portTCP,Network.portUDP);


        Network.Signal signal = new Network.Signal();
        signal.sign = "Something happened";
        client.sendTCP(signal);

        client.start();
    }*/

    @Override
    public void run() {
        try {
            client = new Client();
            client.start();

            Network.register(client);
            //client.addListener();
            InetAddress address = client.discoverHost(54777, 5000);
            client.connect(5000, address, Network.portTCP, Network.portUDP);


            Signal signal = new Signal();
            signal.sign = "Something happened";
            client.sendTCP(signal);


        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
