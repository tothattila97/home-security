package production.toth.attila.homesecurity.Kryonet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.esotericsoftware.kryonet.EndPoint;


public class Network {

    static public final int portTCP = 54555;
    static public final int portUDP = 54777;

    // This registers objects that are going to be sent over the network.
    static public void register (EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.register(Object.class);
        kryo.register(Signal.class, new JavaSerializer());

    }

}

