import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

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

                final byte[] bufResponse = DNSMessage.builder()
                        .writeHeader()
                        .writeQuestion()
                        .writeQuestion()
                        .build();

                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}

class DNSMessage {
    private static final short FIELD_ID = 1234;
    private static final byte FIELD_QR_OPCODE_AA_TC_RD = (byte) 0b10000000;
    private static final byte FIELD_RA_Z_RCODE = 0;
    private static final short FIELD_QDCOUNT = 1;
    private static final short FIELD_ANCOUNT = 1;
    private static final short FIELD_NSCOUNT = 0;
    private static final short FIELD_ARCOUNT = 0;

    private static final short FIELD_TYPE = 1;
    private static final short FIELD_CLASS = 1;

    private ByteBuffer byteBuffer;
    private DNSMessage() {
        this.byteBuffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
    }

    public static DNSMessage builder() {
        return new DNSMessage();
    }

    public DNSMessage writeHeader() {
        this.byteBuffer.putShort(FIELD_ID)
                .put(FIELD_QR_OPCODE_AA_TC_RD)
                .put(FIELD_RA_Z_RCODE)
                .putShort(FIELD_QDCOUNT)
                .putShort(FIELD_ANCOUNT)
                .putShort(FIELD_NSCOUNT)
                .putShort(FIELD_ARCOUNT);
        return this;
    }

    private void encodeDomain(String domain) {
        String[] parts = domain.split("\\.");
        this.byteBuffer.put((byte) parts[0].length())
                .put(parts[0].getBytes(StandardCharsets.UTF_8))
                .put((byte) parts[1].length())
                .put(parts[1].getBytes(StandardCharsets.UTF_8))
                .put((byte) 0);
    }

    public DNSMessage writeQuestion() {
        encodeDomain("codecrafters.io");
        this.byteBuffer.putShort(FIELD_TYPE)
                .putShort(FIELD_CLASS);
        return this;
    }

    public DNSMessage writeAnswer(ByteBuffer byteBuffer) {
        this.writeQuestion();
        this.byteBuffer.putInt(300)
                .putShort((short) 4)
                .put(new byte[] {8, 8, 8, 8});
        return this;
    }

    public byte[] build() {
        return this.byteBuffer.array();
    }
}
