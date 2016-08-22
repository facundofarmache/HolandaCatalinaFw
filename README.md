# Holanda Catalina

## Motivation
Basing needs, to build a specific set of components by itself define a services-based architecture, we decided to provide high-level tools that build the structures necessary for the development of this type of platforms (PaaS)

In the following links a basic set of required components described for this type of platform:
- https://www.nginx.com/blog/introduction-to-microservices/?utm_source=service-discovery-in-a-microservices-architecture&utm_medium=blog
- http://microservices.io/index.html

Currently there are many implementations of general purpose Java development solutions, but none of these implementations is specifically created to develop a set of services interconnected with each other to form a platform.
As an example to create a service that responds to an HTTP REST interface based on java we have to think of a JEE solution, which includes an application server / web, not less libset and are generally systems that require powerful hardware. Unlike this example we propose simple code, without complex environments, without libraries that the solution does not require and are lightweight and flexible solutions without powerful hardware.

## Components
- Http Service Interface
- Service whrapper
- PaaS Protocol
- Shared Memory beetwen instances (Cloud computing)
- Layered arquitecture
- Deployment service

### Http service interface
Entre la herramientas de alto nivel, el entorno cuenta con un potente servidor http de alto rendimiento y a la vez muy simple de usar. Con esta herramienta podemos públicas en forma muy simple interfaces http sin necesidad de infraestructura extra ni grandes requerimientos de hardaware.

####Publishing a local folder
Publishing some folder of your own computer...
```java
HttpServer server = new HttpServer(1338);
server.addContext(new FolderContext("", Paths.get("/home/javaito"));
server.start();
```
Then open your web borwser on http://localhost:1338

Publishing some folder with default element...

First we create a file in the folder that we will publish called default.txt and put the next text into the file.
```txt
Hello world!!
```
then we need to publish a context with a default element
```java
HttpServer server = new HttpServer(1338);
server.addContext(new FolderContext("", Paths.get("/home/javaito", "default.txt"));
server.start();
```

### Service whrapper


### PaaS Protocol


### Shared memory beetwen instances


### Layered arquitecture


### Deployment service
