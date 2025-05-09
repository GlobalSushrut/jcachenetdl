# JCacheNetDL Developer Guide

This guide provides detailed information for developers working on or extending the JCacheNetDL project. It covers architecture, development practices, and contribution guidelines.

## Project Architecture

JCacheNetDL is modularly designed for extensibility:
- **/common**: Shared classes and utilities.
- **/network**: Handles P2P communication using Netty.
- **/node**: Manages caching and file operations.
- **/ledger**: Implements the distributed ledger logic.
- **/api**: REST API server for external interactions.
- **/launcher**: CLI and entry point for the application.

## Setting Up Development Environment

### Requirements
- Java 17 or higher
- Maven 3.6 or higher
- IDE with Java support (e.g., IntelliJ, Eclipse)

### Building the Project
Run:
```
mvn clean package
```
This compiles the code and creates an executable JAR.

### Running Tests
Execute unit tests with:
```
mvn test
```
Ensure all tests pass before committing changes.

## Code Style and Best Practices
- Use meaningful variable and method names.
- Follow Java conventions (e.g., camelCase for variables, PascalCase for classes).
- Add Javadoc comments for all public methods.
- Handle exceptions properly and log errors using SLF4J.

## Contributing
1. Fork the repository.
2. Create a new branch for your feature or fix.
3. Make changes and add tests.
4. Submit a pull request with a clear description.

## Extending the System
- **Adding New Endpoints**: Extend RestApiServer by registering new HttpHandlers.
- **Custom Cache Strategies**: Modify CacheEngine for advanced caching logic.
- **Peer Discovery Enhancements**: Update PeerDiscovery in the network module for new protocols.

## Testing and Debugging

### Unit Testing
JCacheNetDL uses JUnit for unit tests. Run tests with:
```
mvn test
```
Write tests for new features in the `/test` directory, covering edge cases and integration points.

### Debugging Tips
- Use SLF4J logging to trace execution flow.
- Set breakpoints in your IDE to inspect variables during runtime.
- Check for common issues like port conflicts by reviewing configuration files.
- For API debugging, use tools like curl or Postman to test endpoints.

Ensure tests are run before deployments to maintain code quality.

## Debugging and Troubleshooting
- Use SLF4J logging for debugging.
- Check configuration files for port conflicts.
- Run `mvn compile` to catch compilation errors early.

For more details, refer to the README.md for user-facing documentation.
