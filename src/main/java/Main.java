import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");

        // Uncomment this block to pass the first stage

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            while (true) {
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                System.out.println("Received data");

                short field_ID = 1234;
                BitSet field_QR_OPCODE_AA_TC_RD = new BitSet(8);
                field_QR_OPCODE_AA_TC_RD.set(7);
                byte field_RA_Z_RCODE = 0;
                short field_QDCOUNT = 0;
                short field_ANCOUNT = 0;
                short field_NSCOUNT = 0;
                short field_ARCOUNT = 0;

                final byte[] bufResponse = ByteBuffer.allocate(512)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putShort(field_ID)
                        .put(field_QR_OPCODE_AA_TC_RD.toByteArray()[0])
                        .put(field_RA_Z_RCODE)
                        .putShort(field_QDCOUNT)
                        .putShort(field_ANCOUNT)
                        .putShort(field_NSCOUNT)
                        .putShort(field_ARCOUNT)
                        .array();
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
