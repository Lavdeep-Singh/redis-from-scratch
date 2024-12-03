import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ParseRdb {
    private static final int SELECTDB = 0xFE;
    private static final int EXPIRETIME = 0xFD;
    private static final int EXPIRETIMEMS = 0xFC;
    private static final int EOF = 0xFF;
    private static final int METADATA = 0xFA;
    private static final int HASHTABLE = 0xFB;

    private final BufferedWriter output;
    private static final Cache cache = Cache.getInstance();

    public ParseRdb(BufferedWriter output) {
        this.output = output;
    }

    public void readRdbFile(String[] args) throws IOException {
        if (ClientHandler.rdbFilePath == null || ClientHandler.rdbFileName == null) {
            socketWriter("*0\r\n");
            return;
        }

        List<String> keys = parseRdbFile(ClientHandler.rdbFilePath, ClientHandler.rdbFileName);
        writeKeysToOutput(keys);
    }

    private void writeKeysToOutput(List<String> keys) throws IOException {
        output.write(String.format("*%d\r\n", keys.size()));
        for (String key : keys) {
            output.write(String.format("$%d\r\n%s\r\n", key.length(), key));
        }
        output.flush();
    }

    public List<String> parseRdbFile(String filePath, String rdbFileName) throws IOException {
        List<String> keys = new ArrayList<>();
        try (InputStream fis = new FileInputStream(new File(filePath, rdbFileName))) {
            validateRdbHeader(fis);
            parseRdbBody(fis, keys);
        }
        return keys;
    }

    private void validateRdbHeader(InputStream fis) throws IOException {
        byte[] magic = fis.readNBytes(5);
        @SuppressWarnings("unused")
        byte[] version = fis.readNBytes(4);

        String magicString = new String(magic, StandardCharsets.UTF_8);
        if (!magicString.equals("REDIS")) {
            throw new IOException("Invalid RDB file (magic string mismatch).");
        }
    }

    private void parseRdbBody(InputStream fis, List<String> keys) throws IOException {
        int marker;
        while ((marker = fis.read()) != -1) {
            switch (marker) {
                case SELECTDB -> handleSelectDb(fis);
                case EXPIRETIME -> handleExpireTime(fis, keys);
                case EXPIRETIMEMS -> handleExpireTimeMs(fis, keys);
                case EOF -> { return; }
                case METADATA -> handleMetadata(fis);
                case HASHTABLE -> handleHashTable(fis, keys);
            }
        }
    }

    private void handleSelectDb(InputStream fis) throws IOException {
        @SuppressWarnings("unused")
        int dbNumber = fis.read();
    }

    private void handleExpireTime(InputStream fis, List<String> keys) throws IOException {
        fis.readNBytes(4);
        fis.read();
        int keyLength = fis.read();
        String key = parseKey(fis, keyLength);
        keys.add(key);
    }

    private void handleExpireTimeMs(InputStream fis, List<String> keys) throws IOException {
        byte[] bytes = fis.readNBytes(8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long expiry = byteBuffer.getLong();

        int valueType = fis.read();
        int keyLength = fis.read();
        String key = parseKey(fis, keyLength);
        keys.add(key);

        int valueLength = fis.read();
        String value = parseKey(fis, valueLength);
        cache.setWithExpiry(key, value, expiry);
    }

    private void handleMetadata(InputStream fis) throws IOException {
        int nameLength = lengthEncoding(fis, fis.read());
        fis.readNBytes(nameLength);

        int valueLength = lengthEncoding(fis, fis.read());
        fis.readNBytes(valueLength);
    }

    private void handleHashTable(InputStream fis, List<String> keys) throws IOException {
        fis.read(); // hash table size
        fis.read(); // expires hash table size

        int marker;
        while ((marker = fis.read()) != -1 && marker != EOF && marker != EXPIRETIMEMS) {
            int keyLength = fis.read();
            String key = parseKey(fis, keyLength);
            keys.add(key);

            int valueLength = fis.read();
            String value = parseKey(fis, valueLength);
            cache.set(key, value);
        }
    }

    private static String parseKey(InputStream fis, int firstByte) throws IOException {
        int keyLength = lengthEncoding(fis, firstByte);
        byte[] keyBytes = fis.readNBytes(keyLength);
        return new String(keyBytes, StandardCharsets.UTF_8);
    }

    public static int lengthEncoding(InputStream fis, int firstByte) throws IOException {
        // Extract the first 2 bits to determine the encoding type.
        int encodingType = (firstByte >> 6) & 0b11; // Right-shift 6 bits and mask with 0b11 (binary 11).
        /*
         * Example:
         * firstByte = 0b01000001
         * (firstByte >> 6) = 0b01
         * 0b01 & 0b11 = 0b01 (result is 1, indicating 2-byte encoding)
         */

        if (encodingType == 0b00) {
            // 1-byte encoding: Use the remaining 6 bits for length.
            return firstByte & 0b00111111; // Mask out the first 2 bits.
            /*
             * Example:
             * firstByte = 0b00111111
             * Masking: 0b00111111 & 0b00111111 = 63 (length).
             */
        } else if (encodingType == 0b01) {
            // 2-byte encoding: Read the next byte and combine with the current byte.
            int secondByte = fis.read();
            if (secondByte == -1)
                throw new IOException("Unexpected EOF");

            // Combine 14 bits (6 from first byte, 8 from second byte).
            return ((firstByte & 0b00111111) << 8) | secondByte;
            /*
             * Example:
             * firstByte = 0b01000001, secondByte = 0b00001111
             * Mask and shift: (0b01000001 & 0b00111111) << 8 = 0b0000010000000000
             * Combine: 0b0000010000000000 | 0b00001111 = 1039 (length).
             */
        } else if (encodingType == 0b10) {
            // 5-byte encoding: Read the next 4 bytes.
            byte[] lengthBytes = new byte[4];
            if (fis.read(lengthBytes) != 4)
                throw new IOException("Unexpected EOF");

            // Combine 4 bytes into a 32-bit integer (big-endian).
            return ((lengthBytes[0] & 0xFF) << 24) |
                    ((lengthBytes[1] & 0xFF) << 16) |
                    ((lengthBytes[2] & 0xFF) << 8) |
                    (lengthBytes[3] & 0xFF);
            /*
             * Example:
             * lengthBytes = {0x00, 0x00, 0x10, 0x00} => 0x00001000 (length: 4096).
             */
        } else if (encodingType == 0b11) {
            System.out.println("Encoding type: 11 (special marker)");
            // This is a special marker, handle accordingly
            // Typically, the length is derived from custom logic or handled separately
            int specialMarker = fis.read();
            System.out.println("Special marker value: " + specialMarker);
            // For demonstration, return 1 (can be adjusted based on your logic)
            return 1;
        } else {
            // Special marker (11XXXXXX): Not implemented.
            throw new UnsupportedOperationException("Special marker not implemented");
        }
    }

    private void socketWriter(String message) throws IOException {
        output.write(message);
        output.flush();
    }
}
