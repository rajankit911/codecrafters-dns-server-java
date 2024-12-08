import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
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
                final byte[] bufRequest = packet.getData();

                short requestId = ByteBuffer.wrap(bufRequest, 0, 2).getShort();

                byte request_QR_OPCODE_AA_TC_RD = ByteBuffer.wrap(bufRequest, 2, 1).get();
                boolean error = (request_QR_OPCODE_AA_TC_RD & 0b01111000) > 0;
                request_QR_OPCODE_AA_TC_RD &= (byte) 0b01111001;
                request_QR_OPCODE_AA_TC_RD |= (byte) 0b10000000;

                StringBuilder domain = new StringBuilder();
                int baseOffset = 12;
                byte len = ByteBuffer.wrap(bufRequest, baseOffset, 1).get();
                baseOffset += 1;
                int n = 0;
                while (len > 0) {
                    n += len + 1;
                    byte[] subdomain = ByteBuffer.allocate(len).put(bufRequest, baseOffset, len).array();
                    domain.append(new String(subdomain)).append(".");
                    baseOffset += len;
                    len = ByteBuffer.wrap(bufRequest, baseOffset, 1).get();
                    baseOffset += 1;
                }

                String domainName = domain.substring(0, n - 1);

                System.out.println("domain requested: " + domain);
                final byte[] bufResponse = DNSMessage.builder()
                        .writeHeader(requestId, request_QR_OPCODE_AA_TC_RD, error)
                        .writeQuestion(domain.toString())
                        .writeAnswer(domain.toString())
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

    public DNSMessage writeHeader(short requestId, byte request_QR_OPCODE_AA_TC_RD, boolean error) {
        byte field_RA_Z_RCODE = (byte) (error ? 0b00000100 : 0);
        this.byteBuffer.putShort(requestId)
                .put(request_QR_OPCODE_AA_TC_RD)
                .put(field_RA_Z_RCODE)
                .putShort(FIELD_QDCOUNT)
                .putShort(FIELD_ANCOUNT)
                .putShort(FIELD_NSCOUNT)
                .putShort(FIELD_ARCOUNT);
        return this;
    }

    private void encodeDomain(String domain) {
        String[] parts = domain.split("\\.");
        for (String part : parts) {
            this.byteBuffer.put((byte) part.length()).put(part.getBytes(StandardCharsets.UTF_8));
        }

        this.byteBuffer.put((byte) 0);
    }

    public DNSMessage writeQuestion(String domain) {
        encodeDomain(domain);
        this.byteBuffer.putShort(FIELD_TYPE)
                .putShort(FIELD_CLASS);
        return this;
    }

    public DNSMessage writeAnswer(String domain) {
        this.writeQuestion(domain);
        this.byteBuffer.putInt(300)
                .putShort((short) 4)
                .put(new byte[] {8, 8, 8, 8});
        return this;
    }

    public byte[] build() {
        return this.byteBuffer.array();
    }
}
