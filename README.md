# Redis From Scratch 🚀  

Welcome to **Redis From Scratch** — a minimal Redis clone built to understand the internals of Redis. This project walks through the fundamental components of a Redis server, implementing features like in-memory storage, key-value operations, persistence through RDB files, and client-server communication using the RESP protocol.

## Features ✨  
- **Key-Value Store**: Support for basic `SET`, `GET`, and `DEL` commands.  
- **Persistence**: Implements Redis RDB file parsing to load data at startup.  
- **RESP Protocol**: Handles Redis Serialization Protocol (RESP) to communicate with clients.  
- **Multiple Data Types**: Support for strings, lists, and expiration settings.  
- **Command Handling**: Processes common Redis commands like `PING`, `ECHO`, and `KEYS`.  
- **Error Handling**: Gracefully handles unknown commands and file-related issues.  

---

## Project Structure 📂  

```plaintext
├── src  
│   ├── main/java  
│   │   ├── Main.java              # Entry point for the server  
│   │   ├── ClientHandler.java     # Handles client requests  
│   │   ├── RDBParser.java         # Parses RDB files  
│   │   ├── Cache.java             # Manages Cache  
│   │   └── Config.java            # Configuration management  
└── README.md                      # Project documentation  
```

---

## How to Run 🛠️  

### Prerequisites  
- **Java 11+**  
- **Maven**  

### Steps  
1. **Clone the Repository**  
   ```bash  
   git clone https://github.com/Lavdeep-Singh/redis-from-scratch.git  
   cd redis-from-scratch  
   ```  

2. **Build the Project**  
   ```bash  
   javac -d out src/main/java/*.java 
   ```  

3. **Run the Server**  
   ```bash  
   java -cp out src/main/java/Main.java
   ```  

4. **Test with Redis CLI**  
   ```bash  
   redis-cli -p 6379  
   > PING  
   > SET key value  
   > GET key  
   ```

---

## Configuration ⚙️  
You can modify the server's configuration using the `Config` class:  

- **Port**: Default is `6379`.  
- **Data Directory**: Specify the path for RDB files.  
- **DB Filename**: Set the default RDB filename to load at startup.

---

## Example Commands 🗂️  

- **PING**:  
   ```plaintext  
   > PING  
   +PONG  
   ```  

- **SET/GET**:  
   ```plaintext  
   > SET name "Codecrafters"  
   > GET name  
   "Codecrafters"  
   ```  

- **KEYS**:  
   ```plaintext  
   > KEYS *  
   1) "name"  
   ```

---

## Development Notes 📝  

### RDB Parsing  
The project includes a custom RDB parser that reads and decodes binary Redis dump files. If the file does not exist, the server treats the database as empty.  

### Handling Special Encodings  
The parser supports multiple length encoding schemes, including special markers for 32-bit and 64-bit lengths.  

---

## Contributing 🤝  
Contributions are welcome! Feel free to fork the repo, submit issues, or create pull requests.  

1. Fork the repo  
2. Create a new branch  
3. Commit your changes  
4. Open a pull request  

---

## License 📝  
This project is licensed under the MIT License.  

---

## Acknowledgments 🙌  
- Inspired by [Redis](https://redis.io).  
- Thanks to [Codecrafters.io](https://codecrafters.io) for the challenge idea.  

Happy hacking! 🎉