package com.purbon.kafka.topology;

import static com.purbon.kafka.topology.BuilderCLI.ADMIN_CLIENT_CONFIG_OPTION;
import static com.purbon.kafka.topology.BuilderCLI.ALLOW_DELETE_OPTION;
import static com.purbon.kafka.topology.BuilderCLI.BROKERS_OPTION;
import static com.purbon.kafka.topology.BuilderCLI.DRY_RUN_OPTION;
import static com.purbon.kafka.topology.BuilderCLI.QUIET_OPTION;
import static com.purbon.kafka.topology.TopologyBuilderConfig.CONFLUENT_SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.purbon.kafka.topology.api.adminclient.TopologyBuilderAdminClient;
import com.purbon.kafka.topology.backend.RedisBackend;
import com.purbon.kafka.topology.model.Topology;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class KafkaTopologyBuilderTest {

  @Mock TopologyBuilderAdminClient topologyAdminClient;

  @Mock AccessControlProvider accessControlProvider;

  @Mock BindingsBuilderProvider bindingsBuilderProvider;

  @Mock TopicManager topicManager;

  @Mock AccessControlManager accessControlManager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private Map<String, String> cliOps;
  private Properties props;

  @Before
  public void before() {
    cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ADMIN_CLIENT_CONFIG_OPTION, "/fooBar");

    props = new Properties();
    props.put(CONFLUENT_SCHEMA_REGISTRY_URL_CONFIG, "http://foo:8082");
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "");
    props.put(AdminClientConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
  }

  @Test
  public void buildTopicNameTest() throws URISyntaxException, IOException {

    URL dirOfDescriptors = getClass().getResource("/dir");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    Topology topology = TopologyDescriptorBuilder.build(fileOrDirPath);

    assertEquals(4, topology.getProjects().size());
  }

  @Test
  public void closeAdminClientTest() throws Exception {
    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    TopologyBuilderConfig builderConfig = new TopologyBuilderConfig(cliOps, props);

    KafkaTopologyBuilder builder =
        KafkaTopologyBuilder.build(
            fileOrDirPath,
            builderConfig,
            topologyAdminClient,
            accessControlProvider,
            bindingsBuilderProvider);

    builder.close();

    verify(topologyAdminClient, times(1)).close();
  }

  @Test(expected = IOException.class)
  public void verifyProblematicParametersTest() throws Exception {
    String file = "fileThatDoesNotExist.yaml";
    TopologyBuilderConfig builderConfig = new TopologyBuilderConfig(cliOps, props);

    KafkaTopologyBuilder builder =
        KafkaTopologyBuilder.build(
            file,
            builderConfig,
            topologyAdminClient,
            accessControlProvider,
            bindingsBuilderProvider);

    builder.verifyRequiredParameters(file, cliOps);
  }

  @Test(expected = IOException.class)
  public void verifyProblematicParametersTest2() throws Exception {
    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    TopologyBuilderConfig builderConfig = new TopologyBuilderConfig(cliOps, props);
    KafkaTopologyBuilder builder =
        KafkaTopologyBuilder.build(
            fileOrDirPath,
            builderConfig,
            topologyAdminClient,
            accessControlProvider,
            bindingsBuilderProvider);

    builder.verifyRequiredParameters(fileOrDirPath, cliOps);
  }

  @Test
  public void verifyProblematicParametersTestOK() throws Exception {
    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    URL clientConfigURL = getClass().getResource("/client-config.properties");
    String clientConfigFile = Paths.get(clientConfigURL.toURI()).toFile().toString();

    cliOps.put(ADMIN_CLIENT_CONFIG_OPTION, clientConfigFile);

    TopologyBuilderConfig builderConfig = new TopologyBuilderConfig(cliOps, props);
    KafkaTopologyBuilder builder =
        KafkaTopologyBuilder.build(
            fileOrDirPath,
            builderConfig,
            topologyAdminClient,
            accessControlProvider,
            bindingsBuilderProvider);

    builder.verifyRequiredParameters(fileOrDirPath, cliOps);
  }

  @Test
  public void builderRunTestAsFromCLI() throws Exception {

    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    URL clientConfigURL = getClass().getResource("/client-config.properties");
    String clientConfigFile = Paths.get(clientConfigURL.toURI()).toFile().toString();

    Map<String, String> config = new HashMap<>();
    config.put(BROKERS_OPTION, "localhost:9092");
    config.put(ALLOW_DELETE_OPTION, "false");
    config.put(DRY_RUN_OPTION, "false");
    config.put(QUIET_OPTION, "false");
    config.put(ADMIN_CLIENT_CONFIG_OPTION, clientConfigFile);

    KafkaTopologyBuilder builder = KafkaTopologyBuilder.build(fileOrDirPath, config);

    builder.setTopicManager(topicManager);
    builder.setAccessControlManager(accessControlManager);

    doNothing().when(topicManager).apply(anyObject(), anyObject());

    doNothing().when(accessControlManager).apply(anyObject(), anyObject());

    builder.run();
    builder.close();

    verify(topicManager, times(1)).apply(anyObject(), anyObject());
    verify(accessControlManager, times(1)).apply(anyObject(), anyObject());
  }

  @Mock RedisBackend stateProcessor;

  @Test
  public void builderRunTestAsFromCLIWithARedisBackend() throws Exception {

    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    URL clientConfigURL = getClass().getResource("/client-config-redis.properties");
    String clientConfigFile = Paths.get(clientConfigURL.toURI()).toFile().toString();

    Map<String, String> config = new HashMap<>();
    config.put(BROKERS_OPTION, "localhost:9092");
    config.put(ALLOW_DELETE_OPTION, "false");
    config.put(DRY_RUN_OPTION, "false");
    config.put(QUIET_OPTION, "false");
    config.put(ADMIN_CLIENT_CONFIG_OPTION, clientConfigFile);

    KafkaTopologyBuilder builder = KafkaTopologyBuilder.build(fileOrDirPath, config);

    builder.setTopicManager(topicManager);
    builder.setAccessControlManager(accessControlManager);

    doNothing().when(topicManager).apply(anyObject(), anyObject());

    doNothing().when(accessControlManager).apply(anyObject(), anyObject());

    BackendController cs = new BackendController(stateProcessor);
    ExecutionPlan plan = ExecutionPlan.init(cs, System.out);

    builder.run(plan);
    builder.close();

    verify(stateProcessor, times(2)).createOrOpen();
    verify(topicManager, times(1)).apply(anyObject(), anyObject());
    verify(accessControlManager, times(1)).apply(anyObject(), anyObject());
  }

  @Test
  public void buiderRunTest() throws Exception {
    URL dirOfDescriptors = getClass().getResource("/descriptor.yaml");
    String fileOrDirPath = Paths.get(dirOfDescriptors.toURI()).toFile().toString();

    TopologyBuilderConfig builderConfig = new TopologyBuilderConfig(cliOps, props);

    KafkaTopologyBuilder builder =
        KafkaTopologyBuilder.build(
            fileOrDirPath,
            builderConfig,
            topologyAdminClient,
            accessControlProvider,
            bindingsBuilderProvider);

    builder.setTopicManager(topicManager);
    builder.setAccessControlManager(accessControlManager);

    doNothing().when(topicManager).apply(anyObject(), anyObject());

    doNothing().when(accessControlManager).apply(anyObject(), anyObject());

    builder.run();
    builder.close();

    verify(topicManager, times(1)).apply(anyObject(), anyObject());
    verify(accessControlManager, times(1)).apply(anyObject(), anyObject());
  }
}
