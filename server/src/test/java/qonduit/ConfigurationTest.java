package qonduit;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import qonduit.Configuration;
import qonduit.SpringBootstrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        EnvironmentTestUtils.addEnvironment(this.context,
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
                "qonduit.security.ssl.use-generated-keypair:true");
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

    @Test(expected = BeanCreationException.class)
    public void testMissingSSLProperty() throws Exception {
        context.register(SpringBootstrap.class);
        // @formatter:off
        EnvironmentTestUtils.addEnvironment(this.context,
                "qonduit.server.ip:127.0.0.1",
                "qonduit.server.tcp-port:54321",
                "qonduit.server.udp-port:54325",
                "qonduit.http.ip:127.0.0.1",
                "qonduit.http.port:54322",
                "qonduit.websocket.ip:127.0.0.1",
                "qonduit.websocket.port:54323",
                "qonduit.accumulo.zookeepers:localhost:2181",
                "qonduit.accumulo.instance-name:test",
                "qonduit.accumulo.username:root",
                "qonduit.accumulo.password:secret",
                "qonduit.http.host:localhost");
        // @formatter:on
        context.refresh();
    }

    @Test
    public void testSSLProperty() throws Exception {
        context.register(SpringBootstrap.class);
        // @formatter:off
        EnvironmentTestUtils.addEnvironment(this.context,
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
                "qonduit.security.ssl.key-file:/tmp/bar");
        // @formatter:on
        context.refresh();
    }

}
