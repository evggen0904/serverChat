package settings;


import java.io.*;

public class ObjectSerialization {

    public byte[] toBytes(Object object) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);

        return baos.toByteArray();
    }

    public Object toObject(byte[] bytes) throws Exception{

        return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();

    }
}
