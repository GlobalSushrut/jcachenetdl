# JCacheNetDL - P2P CDN + Distributed Ledger Framework

JCacheNetDL is a peer-to-peer content delivery network with distributed ledger functionality implemented in Java. It allows nodes to cache and serve files to peers, while tracking all activity in an immutable distributed ledger.

## Features

- **P2P Network**: Automatic peer discovery and communication
- **File Caching**: Files are split into chunks, compressed, and cached
- **Distributed Ledger**: All actions are recorded in a blockchain-style ledger
- **Torrent-Style File Fetching**: Files can be fetched in chunks from multiple peers
- **CLI Interface**: Simple command-line interface to interact with the system

## What is JCacheNetDL?

JCacheNetDL is an open-source, peer-to-peer content delivery network (CDN) with integrated distributed ledger technology, built in Java. It enables efficient file sharing and caching across a decentralized network, similar to a blockchain-based CDN, ensuring data integrity and scalability for applications like content distribution and edge computing.

## What Can JCacheNetDL Do?

- **Cache Management**: Store and retrieve files in chunks with in-memory caching, supporting operations like add, list, and fetch.
- **REST API Endpoints**: Interact programmatically with endpoints such as /api/cache/add, /api/cache/list, /api/status, /api/metrics, and /api/ledger for monitoring and management.
- **P2P Networking**: Automatic peer discovery, communication, and file fetching from multiple sources.
- **Distributed Ledger**: Record all actions immutably, providing auditability and security.
- **Metrics and Monitoring**: Track system performance, memory usage, and cache stats in real-time.
- **Error Handling**: Robust responses for invalid inputs, ensuring reliable operation in production.

## How to Use JCacheNetDL

### Running the Application
1. Build the project with Maven:
   ```bash
   mvn clean package
   ```
2. Start a node:
   ```bash
   java -jar target/jcachenetdl-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```
   Use environment variables or config files to set ports (e.g., NODE_PORT, API_PORT).

### CLI Commands
- `help`: List available commands.
- `addpeer <host:port>`: Manually add a peer.
- `upload <filepath>`: Upload a file and get its hash.
- `fetch <filehash> [path]`: Download a file from the network.

### REST API Usage
- **Add Cache Item**: `curl -X POST http://localhost:8084/api/cache/add -H "Content-Type: application/json" -d '{"key":"key","value":"value"}'`
- **List Cache Items**: `curl http://localhost:8084/api/cache/list`
- **Get Status**: `curl http://localhost:8084/api/status` for node metrics.

## Advantages over Traditional CDNs
JCacheNetDL improves on traditional CDNs like Cloudflare by leveraging a decentralized P2P architecture, reducing reliance on central servers, lowering costs, and enhancing scalability. Its rate limiting and swarm management ensure better security and efficiency, handling high traffic with less latency and resource use.

## Use Cases
- Distributing large files (e.g., software updates) via P2P for faster downloads in bandwidth-constrained areas.
- Content delivery for streaming services, where decentralization minimizes server overload during peak times.
- Peer-to-peer sharing in collaborative apps, competing with BitTorrent by adding security features like rate limiting.

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

## Building

To build the project:

```bash
mvn clean package
```

This will create an executable JAR in the target directory.

## Running

Start a node:

```bash
java -jar target/jcachenetdl-1.0-SNAPSHOT-jar-with-dependencies.jar [port]
```

The port parameter is optional and defaults to 8080.

## Usage

Once the node is started, you can use the following commands:

- `help` - Show available commands
- `addpeer <host:port>` - Add a peer manually
- `upload <filepath>` - Upload a file to the network
- `fetch <filehash> [path]` - Fetch a file from the network
- `stats` - Show node statistics
- `peers` - List connected peers
- `ledger` - Show ledger information
- `stop` - Stop the node
- `exit`, `quit` - Exit the application

## Example Usage

1. Start the first node:
   ```
   java -jar target/jcachenetdl-1.0-SNAPSHOT-jar-with-dependencies.jar 8080
   ```

2. Start the second node and connect to the first:
   ```
   java -jar target/jcachenetdl-1.0-SNAPSHOT-jar-with-dependencies.jar 8081
   > addpeer localhost:8080
   ```

3. Upload a file from the first node:
   ```
   > upload /path/to/file.zip
   ```
   This will output a file hash.

4. Fetch the file from the second node:
   ```
   > fetch <file_hash> downloaded_file.zip
   ```

## Architecture

The system consists of the following components:

- **Network Layer**: Handles peer-to-peer communication
- **Cache Engine**: Manages file chunks storage and retrieval
- **Ledger**: Records all actions in a blockchain-style distributed ledger
- **File Server**: Orchestrates file uploads and downloads
- **Node Launcher**: Provides the CLI interface and coordinates all components

## Development

The project uses a modular structure:

- `/common` - Shared classes
- `/network` - P2P socket layer
- `/node` - Cache engine + file serving
- `/ledger` - Distributed ledger logic
- `/launcher` - CLI app & entry point
- `/util` - Helper utilities

## Future Enhancements

- Security layer with RSA signatures
- Encrypted cache with AES-256
- Web interface
- Performance optimizations
- Advanced peer discovery mechanisms
