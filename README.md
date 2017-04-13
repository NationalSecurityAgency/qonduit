[![Apache License][li]][ll]

Qonduit is a secure web socket proxy for [Apache Accumulo] (http://accumulo.apache.org/). 

Qonduit is a Java server process that uses [Netty] (http://netty.io/) for a secure web socket transport and [Spring] (https://spring.io/) for pluggable security modules. Qonduit uses [Jackson] (https://github.com/FasterXML/jackson) for serialization and deserialization of [CBOR] (http://cbor.io/) encoded request/response objects. Qonduit discovers custom request and response types and server side logic using the Java Service Loader mechanism. Qonduit differs from the Accumulo [Proxy] (http://accumulo.apache.org/1.8/accumulo_user_manual.html#_proxy) in the following ways:

1. Qonduit uses secure web sockets to provide an asychronous and secure transport between the client and the Qonduit server.
2. Qonduit can optionally authenticate users using client supplied credentials and a Spring Security configuration on the Qonduit server.
3. Qonduit does not provide a strict proxy for the Accumulo client API, it is designed to be extended with pluggable server side logic and custom request and response types.

[li]: http://img.shields.io/badge/license-ASL-blue.svg
[ll]: https://www.apache.org/licenses/LICENSE-2.0
