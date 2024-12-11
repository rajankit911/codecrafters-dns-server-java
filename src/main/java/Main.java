import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");

        String[] resolverFlag = args[1].split(":");
        String resolverIP = resolverFlag[0];
        int resolverPort = Integer.parseInt(resolverFlag[1]);
        SocketAddress resolverAddress = new InetSocketAddress(resolverIP, resolverPort);

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
                List<String> domains = dnsRequest.getDomains();
                Map<String, byte[]> addressByDomainMap = new HashMap<>();

                for (String domain : domains) {
                    byte[] forwardRequestBuf = DNSMessage.builder()
                            .setDomains(Collections.singletonList(domain))
                            .writeHeader(requestId, reqFlags, (short) 1, (short) 0)
                            .writeQuestion()
                            .build();

                    DatagramPacket forwardRequestPacket =  new DatagramPacket(forwardRequestBuf, forwardRequestBuf.length, resolverAddress);
                    serverSocket.send(forwardRequestPacket);

                    byte [] forwardResponseBuf = new byte[512];
                    DatagramPacket forwardResponsePacket = new DatagramPacket(forwardResponseBuf, forwardResponseBuf.length);
                    serverSocket.receive(forwardResponsePacket);

                    DNSMessage forwardResponse = new DNSMessage(forwardResponseBuf);
                    byte[] ip = forwardResponse.getResolvedAddress();
                    addressByDomainMap.put(domain, ip);
                }

                int opcode = reqFlags & 0x7800;
                int rd = reqFlags & 0x0100;
                int rcode = opcode > 0 ? 4 : 0;
                short resFlags = (short) (0x8000 | opcode | rd | rcode);

                final byte[] bufResponse = DNSMessage.builder()
                        .setDomains(domains)
                        .setAddressByDomainMap(addressByDomainMap)
                        .writeHeader(requestId, resFlags, qdcount, qdcount)
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

    private short type = 1;
    private short qclass = 1;

    private List<String> domains;
    private Map<String, byte[]> addressByDomainMap;
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

    public List<String> getDomains() {
        return this.domains;
    }

    public DNSMessage setDomains(List<String> domains) {
        this.domains = domains;
        return this;
    }

    public DNSMessage setAddressByDomainMap (Map<String, byte[]> addressByDomainMap) {
        this.addressByDomainMap = addressByDomainMap;
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
        this.domains = new ArrayList<>(this.qdcount);
    }

    private void readQuestion() {
        for (int i = 0; i < this.qdcount; i++) {
            this.domains.add(this.decodeDomain());
            this.type = this.byteBuffer.getShort();
            this.qclass = this.byteBuffer.getShort();
        }
    }

    public byte[] getResolvedAddress() {
        this.decodeDomain();
        this.byteBuffer.getShort(); // TYPE
        this.byteBuffer.getShort(); // CLASS
        this.byteBuffer.getInt(); // TTL
        this.byteBuffer.getShort(); // RDATA Length

        byte[] ip = new byte[4];
        this.byteBuffer.get(ip);
        return ip;
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

    public DNSMessage writeHeader(short requestId, short flags, short qdcount, short ancount) {
        this.requestId = requestId;
        this.flags = flags;
        this.qdcount = qdcount;
        this.ancount = ancount;

        this.byteBuffer.putShort(this.requestId)
                .putShort(this.flags)
                .putShort(this.qdcount)
                .putShort(this.ancount)
                .putShort(this.nscount)
                .putShort(this.arcount);

        return this;
    }

    private void encodeDomain(String domain) {
        String[] parts = domain.split("\\.");
        for (String part : parts) {
            this.byteBuffer.put((byte) part.length())
                    .put(part.getBytes(StandardCharsets.UTF_8));
        }

        this.byteBuffer.put((byte) 0);
    }

    public DNSMessage writeQuestion() {
        for (String domain : this.domains) {
            this.encodeDomain(domain);
            this.byteBuffer.putShort(type)
                    .putShort(qclass);
        }

        return this;
    }

    public DNSMessage writeAnswer() {
        for (String domain : this.domains) {
            // question section
            this.encodeDomain(domain);
            this.byteBuffer.putShort(type)
                    .putShort(qclass);
            // answer section
            this.byteBuffer.putInt(300)
                    .putShort((short) 4)
                    .put(addressByDomainMap.get(domain));
        }

        return this;
    }

    public byte[] build() {
        return this.byteBuffer.array();
    }

}
