package qonduit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @After
    public void closeContext() {
        context.close();
    }

    @Test
    public void testMinimalConfiguration() throws Exception {
        context.register(SpringBootstrap.class);
        // @formatter:off
        TestPropertyValues.of(
                "qonduit.server.ip:127.0.0.1",
                "qonduit.http.ip:127.0.0.1",
                "qonduit.http.port:54322",
                "qonduit.websocket.ip:127.0.0.1",
                "qonduit.websocket.port:54323",
                "qonduit.accumulo.zookeepers:localhost:2181",
                "qonduit.accumulo.instance-name:test",
                "qonduit.accumulo.username:root",
                "qonduit.accumulo.password:secret",
                "qonduit.http.host:localhost",
                "qonduit.security.ssl.use-generated-keypair:true").applyTo(this.context);
        // @formatter:on
        context.refresh();
        Configuration config = this.context.getBean(Configuration.class);
        assertEquals("127.0.0.1", config.getServer().getIp());
        assertEquals(54322, config.getHttp().getPort());
        assertEquals(54323, config.getWebsocket().getPort());
        assertEquals("localhost:2181", config.getAccumulo().getZookeepers());
        assertEquals("test", config.getAccumulo().getInstanceName());
        assertEquals("root", config.getAccumulo().getUsername());
        assertEquals("secret", config.getAccumulo().getPassword());
        assertEquals("localhost", config.getHttp().getHost());
        assertTrue(config.getSecurity().getSsl().isUseGeneratedKeypair());
    }

    @Test
    public void testMissingSSLProperty() throws Exception {
        context.register(SpringBootstrap.class);
        // @formatter:off
        TestPropertyValues.of(
                "qonduit.server.ip:127.0.0.1",
                "qonduit.http.ip:127.0.0.1",
                "qonduit.http.port:54322",
                "qonduit.websocket.ip:127.0.0.1",
                "qonduit.websocket.port:54323",
                "qonduit.accumulo.zookeepers:localhost:2181",
                "qonduit.accumulo.instance-name:test",
                "qonduit.accumulo.username:root",
                "qonduit.accumulo.password:secret",
                "qonduit.http.host:localhost",
                "qonduit.security.ssl.use-generated-keypair:false").applyTo(this.context);
        // @formatter:on
        context.refresh();
    }

    @Test
    public void testSSLProperty() throws Exception {
        context.register(SpringBootstrap.class);
        // @formatter:off
        TestPropertyValues.of(
                "qonduit.server.ip:127.0.0.1",
                "qonduit.http.ip:127.0.0.1",
                "qonduit.http.port:54322",
                "qonduit.websocket.ip:127.0.0.1",
                "qonduit.websocket.port:54323",
                "qonduit.accumulo.zookeepers:localhost:2181",
                "qonduit.accumulo.instance-name:test",
                "qonduit.accumulo.username:root",
                "qonduit.accumulo.password:secret",
                "qonduit.http.host:localhost",
                "qonduit.security.ssl.certificate-file:/tmp/foo",
                "qonduit.security.ssl.key-file:/tmp/bar").applyTo(this.context);
        // @formatter:on
        context.refresh();
    }

}
