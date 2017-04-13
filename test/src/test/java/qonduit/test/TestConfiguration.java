package qonduit.test;

import qonduit.Configuration;

public class TestConfiguration {

    public static final String HTTP_ADDRESS_DEFAULT = "localhost";

    public static final int WAIT_SECONDS = 2;

    public static Configuration createMinimalConfigurationForTest() {
        // @formatter:off
        Configuration cfg = new Configuration()
                .getServer().setIp("127.0.0.1")
                .getServer().setShutdownQuietPeriod(0)
                .getHttp().setIp("127.0.0.1")
                .getHttp().setPort(54322)
                .getHttp().setHost("localhost")
                .getWebsocket().setIp("127.0.0.1")
                .getWebsocket().setPort(54323)
                .getWebsocket().setTimeout(20)
                .getAccumulo().setZookeepers("localhost:2181")
                .getAccumulo().setInstanceName("test")
                .getAccumulo().setUsername("root")
                .getAccumulo().setPassword("secret")
                .getAccumulo().getWrite().setLatency("100ms")
                .getSecurity().getSsl().setUseGeneratedKeypair(true);
        // @formatter:on

        return cfg;
    }

}
