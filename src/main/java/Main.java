import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

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

                DNSMessage dnsRequest = new DNSMessage(bufRequest);
                short requestId = dnsRequest.getRequestId();
                short reqFlags = dnsRequest.getFlags();
                short qdcount = dnsRequest.getQdcount();
                String domain = dnsRequest.getDomain();

                int opcode = reqFlags & 0x7800;
                int rd = reqFlags & 0x0100;
                int rcode = opcode > 0 ? 4 : 0;
                short resFlags = (short) (0x8000 | opcode | rd | rcode);

                final byte[] bufResponse = DNSMessage.builder()
                        .setDomain(domain)
                        .writeHeader(requestId, resFlags, qdcount)
                        .writeQuestion()
                        .writeAnswer()
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
    private short requestId = 1234;
    private short flags;
    private short qdcount = 0;
    private short ancount = 0;
    private short nscount = 0;
    private short arcount = 0;

    private String domain;
    private short type = 1;
    private short qclass = 1;

    private final ByteBuffer byteBuffer;
    private DNSMessage() {
        this.byteBuffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
    }

    public DNSMessage(byte[] message) {
        this.byteBuffer = ByteBuffer.wrap(message);
        this.readHeader();
        this.readQuestion();
    }

    public short getRequestId() {
        return this.requestId;
    }

    public short getFlags() {
        return this.flags;
    }

    public short getQdcount() {
        return this.qdcount;
    }

    public String getDomain() {
        return this.domain;
    }

    public DNSMessage setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public static DNSMessage builder() {
        return new DNSMessage();
    }

    private void readHeader() {
        this.requestId = this.byteBuffer.getShort();
        this.flags = this.byteBuffer.getShort();
        this.qdcount = this.byteBuffer.getShort();
        this.ancount = this.byteBuffer.getShort();
        this.nscount = this.byteBuffer.getShort();
        this.arcount = this.byteBuffer.getShort();
    }

    private void readQuestion() {
        for (int i = 0; i < this.qdcount; i++) {
            this.domain = this.decodeDomain();
            this.type = this.byteBuffer.getShort();
            this.qclass = this.byteBuffer.getShort();
        }
    }

    private String decodeDomain() {
        StringJoiner domain = new StringJoiner(".");
        byte len;
        while ((len = this.byteBuffer.get()) > 0) {
            byte[] dst = new byte[len];
            this.byteBuffer.get(dst);
            domain.add(new String(dst));
        }

        System.out.println("domain requested: " + domain);
        return domain.toString();
    }

    public DNSMessage writeHeader(short requestId, short flags, short qdcount) {
        this.requestId = requestId;
        this.flags = flags;
        this.qdcount = qdcount;
        this.ancount = qdcount;

        this.byteBuffer.putShort(this.requestId)
                .putShort(this.flags)
                .putShort(this.qdcount)
                .putShort(this.ancount)
                .putShort(this.nscount)
                .putShort(this.arcount);

        return this;
    }

    private void encodeDomain() {
        String[] parts = this.domain.split("\\.");
        for (String part : parts) {
            this.byteBuffer.put((byte) part.length())
                    .put(part.getBytes(StandardCharsets.UTF_8));
        }

        this.byteBuffer.put((byte) 0);
    }

    public DNSMessage writeQuestion() {
        for (int i = 0; i < this.qdcount; i++) {
            this.encodeDomain();
            this.byteBuffer.putShort(type)
                    .putShort(qclass);
        }

        return this;
    }

    public DNSMessage writeAnswer() {
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
