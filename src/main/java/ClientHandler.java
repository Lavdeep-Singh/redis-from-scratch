import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader input;
    private BufferedWriter output;
    static String rdbFilePath;
    static String rdbFileName;
    private Cache cache = Cache.getInstance();
    private ParseRdb rdbParser;
    private final Config config;

    public ClientHandler(Socket socket, Config config) {
        this.socket = socket;
        this.config = config;
        rdbFilePath = config.getConfig("dir");
        rdbFileName = config.getConfig("dbfilename");
    }

    private void socketWriter(String outputString) throws IOException {
        output.write(outputString);
        output.flush();
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            rdbParser = new ParseRdb(output);
            // input = *2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n
            while (true) {
                String line = input.readLine(); // line = *2
                if (line == null || line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("*")) {
                    int argCount = Integer.parseInt(line.substring(1)); // parses only integers as string, argCount = 2
                    String[] args = new String[argCount];
                    for (int i = 0; i < argCount; i++) {
                        input.readLine(); // Skip the "$<length>" line, skips $4 and $3
                        args[i] = input.readLine(); // adds ECHO and hey
                    }

                    handleCommand(args);
                } else {
                    socketWriter("-ERR Unknown command format\r\n");
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private void handleCommand(String[] args) throws IOException {
        // args = [ECHO, hey]
        String command = args[0].toUpperCase();

        switch (command) {
            case "PING":
                handlePingCommand(args);
                break;
            case "ECHO":
                handleEchoCommand(args);
                break;
            case "SET":
                handleSetCommand(args);
                break;
            case "GET":
                handleGetCommand(args);
                break;
            case "CONFIG":
                handleConfigCommand(args);
                break;
            case "KEYS":
                rdbParser.readRdbFile(args);
                break;
            default:
                socketWriter("-ERR Unknown command\r\n");
        }
    }

    private void handlePingCommand(String[] args) throws IOException {
        if (args.length == 1) {
            socketWriter("+PONG\r\n");
        } else {
            socketWriter(String.format("$%d\r\n%s\r\n", args[1].length(), args[1]));
        }
    }

    private void handleEchoCommand(String[] args) throws IOException {
        if (args.length != 2) {
            socketWriter("-ERR Wrong number of arguments for 'ECHO' command\r\n");
        } else {
            String message = args[1];
            socketWriter(String.format("$%d\r\n%s\r\n", message.length(), message));
        }
    }

    private void handleConfigCommand(String[] args) throws IOException {
        if (args.length < 3) {
            socketWriter("-ERR Wrong number of arguments for 'CONFIG' command\r\n");
            return;
        }

        // String subCommand = args[1];

        String key = args[2];

        String configValue = config.getConfig(key);

        if (configValue != null) {
            socketWriter(String.format("*2\r\n$3\r\ndir\r\n$%d\r\n%s\r\n", configValue.length(), configValue));
        } else {
            socketWriter("$-1\r\n");
        }
    }

    private void handleSetCommand(String[] args) throws IOException {
        if (args.length < 3) {
            socketWriter("-ERR Wrong number of arguments for 'SET' command\r\n");
            return;
        }

        String key = args[1];
        String value = args[2];
        long expirationTime = 0;

        // Handle optional PX (expiry in milliseconds)
        if (args.length > 3) {
            String option = args[3].toUpperCase();
            if ("PX".equals(option) && args.length == 5) {
                try {
                    expirationTime = System.currentTimeMillis() + Long.parseLong(args[4]);
                } catch (NumberFormatException e) {
                    socketWriter("-ERR Invalid PX value\r\n");
                    return;
                }
            } else {
                socketWriter("-ERR Unsupported option for 'SET' command\r\n");
                return;
            }
        }

        // Store the key-value pair
        cache.setWithExpiry(key, value, expirationTime);
        socketWriter("+OK\r\n");
    }

    private void handleGetCommand(String[] args) throws IOException {
        if (args.length != 2) {
            socketWriter("-ERR Wrong number of arguments for 'GET' command\r\n");
            return;
        }

        String key = args[1];

        // if key not found, reload from rdb
        if (cache.get(key) == null) {
            rdbParser.parseRdbFile(rdbFilePath, rdbFileName);
        }

        // Check expiration
        if (isKeyExpired(key)) {
            cache.remove(key);
            socketWriter("$-1\r\n");
        } else {
            String value = cache.get(key);
            if (value != null) {
                socketWriter(String.format("$%d\r\n%s\r\n", value.length(), value));
            } else {
                socketWriter("$-1\r\n");
            }
        }
    }

    private boolean isKeyExpired(String key) {
        Long expirationTime = cache.getExpiryTime(key);
        return expirationTime != null && expirationTime < System.currentTimeMillis();
    }
}

/*
 * Bulk strings
 * $<length>\r\n<data>\r\n
 * Above is the format in which redis replies where length = length of response
 * data string and data is the actual data
 * learn more here https://redis.io/docs/latest/develop/reference/protocol-spec/
 *
 * $ redis-cli ECHO hey
 * above command is received as *2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n , where \n is
 *
 * General Structure
 * Redis commands are sent as arrays of bulk strings.
 * Each command starts with the * character, followed by the number of elements
 * in the array (arguments, including the command itself).
 * Each element of the array (a string) is prefixed by $, followed by the length
 * of the string
 * 
 * 3 # 3 elements: "SET", "key", and "value"
 * $3 # Length of the first string ("SET")
 * SET # Command
 * $3 # Length of the second string ("key")
 * key # Key
 * $5 # Length of the third string ("value")
 * value # Value
 * px # Optional command to set time in milliseconds
 * $3 # Length of the time string
 * 100 # Milliseconds
 * 
 * Each line ends with \r\n (carriage return + newline).
 * 
 * RESP defines five types of data, identified by their first character:
 * 
 * Simple Strings (+)
 * Represent simple, single-line text responses.
 * Example: +OK\r\n
 * Used for successful commands (e.g., SET).
 * 
 * Errors (-)
 * Represent errors in the command or execution.
 * Example: -ERR Unknown command\r\n
 * Used for invalid or unsupported commands.
 * 
 * Integers (:)
 * Represent numbers (e.g., counters or results of commands like INCR).
 * Example: :100\r\n
 * 
 * Bulk Strings ($)
 * Represent strings with a specified length.
 * Format: $<length>\r\n<string>\r\n
 * Example: $5\r\nhello\r\n
 * 
 * Arrays (*)
 * Represent an ordered list of RESP types.
 * Format: *<number of elements>\r\n...
 * Example (an array of 3 strings):
 * 
 * 3
 * $3\r\nSET\r\n
 * $3\r\nkey\r\n
 * $5\r\nvalue\r\n
 */

/*
 * ########################
 * ### Numeric Literals in Java ###
 * ########################
 *
 * In Java, prefixes are used to specify the base (radix) of numeric literals.
 * 
 * ## Binary Representation ##
 * - Prefix: `0b` (base 2)
 * - Consists only of digits `0` and `1`.
 * - Useful for bitwise operations, masks, and hardware-related programming.
 *
 * Example:
 * int binaryNumber = 0b1010; // 0b1010 = 10 in decimal.
 * int mask = 0b1111; // Mask with all bits set (15 in decimal).
 * 
 * ## Hexadecimal Representation ##
 * - Prefix: `0x` (base 16)
 * - Uses digits `0-9` and letters `A-F` (where A=10, B=11, ..., F=15).
 * - Compact way to represent binary values (4 bits per hex digit).
 * - Common in memory addresses, color codes, and byte manipulations.
 *
 * Example:
 * int hexNumber = 0x1A; // 0x1A = 26 in decimal.
 * int colorCode = 0xFF5733; // RGB color in hex format.
 * 
 * ## Bitwise Operations ##
 * - Binary literals are often used in bitwise operations like AND, OR, XOR, and
 * shifts.
 *
 * Example:
 * int result = 0b1100 & 0b1010; // AND operation: result = 0b1000 (8 in
 * decimal).
 * int shifted = 0b0001 << 2; // Left shift: result = 0b0100 (4 in decimal).
 * 
 * ## Combining Binary and Hexadecimal ##
 * - Hexadecimal is more compact for long binary sequences.
 * - 1 hex digit = 4 binary digits (bits).
 * 
 * Example:
 * int compactHex = 0xF0; // 0xF0 = 11110000 in binary.
 * int expandedBinary = 0b11110000; // Same as above.
 *
 * ## Why Use Prefixes? ##
 * - Prefixes clarify the base of the literal.
 * - Without prefixes, numbers are assumed to be decimal (base 10).
 * 
 * Example:
 * int decimal = 10; // Decimal (base 10).
 * int binary = 0b1010; // Binary (base 2).
 * int hex = 0xA; // Hexadecimal (base 16).
 * 
 * ########################
 * ### Notes Summary ###
 * ########################
 * - `0b` for binary (e.g., 0b1010 = 10 in decimal).
 * - `0x` for hexadecimal (e.g., 0x1F = 31 in decimal).
 * - Binary is great for bit manipulation.
 * - Hex is more readable for long binary sequences.
 * 
 */
