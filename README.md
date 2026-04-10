# LimeWire Network Java SDK

`lmwrntwrk-sdk-java` is the official Java SDK for [LimeWire Network](https://limewire.network), a decentralized file storage network. It allows developers to easily integrate decentralized storage into their Java applications using familiar S3-compatible APIs.

## Prerequisites

- **Java 8 or later**.

## Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>network.limewire.sdk</groupId>
    <artifactId>sdk-java</artifactId>
    <version>0.1.2</version>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```gradle
implementation 'network.limewire.sdk:sdk-java:0.1.2'
```

## Example Usage

A complete, runnable example demonstrating how to initialize the SDK and perform S3 operations (upload, download, list, presign) can be found in [src/test/java/network/limewire/sdk/ExampleApp.java](src/test/java/network/limewire/sdk/ExampleApp.java).

## How it Works

The LimeWire Network Java SDK seamlessly integrates with the official AWS SDK for Java 2.x to provide a familiar S3-compatible interface for decentralized storage. By providing a custom `SdkHttpClient` (or `SdkAsyncHttpClient`) and an endpoint provider, it allows developers to use standard S3 operations while the SDK handles the underlying decentralized routing, request signing, and data validation. This approach enables you to leverage the full power of the AWS SDK ecosystem to interact with the LimeWire Network as a storage backend. For more information, please visit the official [LimeWire Network website](https://limewire.network).

## Documentation

- [CHANGELOG.md](CHANGELOG.md) - Latest updates and breaking changes.
