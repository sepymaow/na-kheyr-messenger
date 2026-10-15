# Na-Kheyr Multi-Client Messenger

A terminal-based client-server messaging application built with Java TCP sockets. The project demonstrates concurrent connection handling, a custom binary protocol, public and direct messaging, acknowledgement-based delivery, offline queues, persistent user profiles, and administrative controls.

## Highlights

- Concurrent client handling through a fixed server thread pool
- Registration and login with persistent local profiles
- Public lounge messages broadcast to connected users
- Direct messages using unique message identifiers
- Delivery acknowledgements with a five-second timeout
- Offline message queues backed by memory and local disk storage
- Duplicate-session prevention for user accounts
- Message statistics for sent and received messages
- Password changes and profile inspection through client commands
- Administrator commands for listing, muting, and removing users
- Graceful server shutdown with queued-message persistence
- Configurable server port and client host through command-line arguments

## Protocol Design

Every network message uses a length-prefixed binary frame:

    +------------------+---------------+--------------------+
    | Length (4 bytes) | Type (1 byte) | Payload (variable) |
    +------------------+---------------+--------------------+

The length field contains the combined size of the type byte and payload. Supported frame types include lounge messages, direct messages, acknowledgements, registration, login, system notices, and administrator commands.

Direct-message payloads contain:

    +----------------------+--------------------------+------------------+-----------------+
    | Message ID (4 bytes) | Username length (1 byte) | Username (UTF-8) | Message (UTF-8) |
    +----------------------+--------------------------+------------------+-----------------+

## Project Structure

    na-kheyr-messenger/
    ├── client/
    │   ├── RelayClient.java          Client entry point and authentication flow
    │   ├── ConsoleReaderTask.java    User input and outgoing messages
    │   └── ServerListenerTask.java   Incoming messages and acknowledgements
    ├── server/
    │   ├── RelayServer.java          Server entry point and admin console
    │   ├── ConnectionThread.java     Per-client protocol and message handling
    │   ├── SessionManager.java       Active sessions, mutes, and ACK timeouts
    │   ├── ProfileManager.java       Account persistence and message statistics
    │   └── OfflineQueueManager.java  Memory and disk-backed offline delivery
    └── shared/
        └── Frame.java                Binary framing and payload encoding

## Requirements

- JDK 11 or later
- No external dependencies

## Compile

### Windows PowerShell

    New-Item -ItemType Directory -Force out | Out-Null
    $sources = Get-ChildItem client, server, shared -Filter *.java | ForEach-Object FullName
    javac -encoding UTF-8 -d out $sources

### Linux or macOS

    mkdir -p out
    find client server shared -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d out

## Run

Start the server in the first terminal. The port is optional and defaults to 8080:

    java -cp out server.RelayServer 8080

Start one or more clients in separate terminals. Host and port are optional and default to 127.0.0.1 and 8080:

    java -cp out client.RelayClient 127.0.0.1 8080

## Client Commands

| Input | Action |
| --- | --- |
| Any ordinary text | Send a message to the public lounge |
| **@username message** | Send a direct message |
| **/profile** | Display the current profile and message statistics |
| **/change_password old new** | Change the account password |
| **exit** | Disconnect the client |

## Administrator Commands

Enter these commands in the server terminal:

| Command | Action |
| --- | --- |
| **/list** | List online users |
| **/kick username** | Disconnect a user |
| **/mute username seconds** | Temporarily block a user from sending messages |
| **/shutdown** | Notify clients, persist queues, and stop the server |

## Persistence

The server creates two local runtime directories:

- **profiles/** stores serialised user profiles and message statistics.
- **offline_data/** stores queued direct messages when the in-memory threshold is exceeded or the server shuts down.

Both directories are excluded from version control.

## Security Scope

This is an educational networking project, not a production messaging service. Communication is not encrypted, and the coursework version uses local Java serialisation for profile and queue persistence. A production implementation should use TLS, salted password hashing, validated usernames, a database, structured logging, and stricter frame-size validation.

## Author

Developed by [Sepanta Saeidi](https://github.com/sepymaow) as an individual Java networking project.
