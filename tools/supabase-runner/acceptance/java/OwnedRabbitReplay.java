package com.checkinn.db;
import java.nio.charset.StandardCharsets;
import com.rabbitmq.client.*;
public final class OwnedRabbitReplay {
  public static void main(String[] args)throws Exception {
    String input=new String(System.in.readAllBytes(),StandardCharsets.UTF_8);
    var n=HostedRunner.JSON.readTree(input);
    String vhost=System.getenv("SUPABASE_DEV_RABBIT_VHOST");
    HostedRunner.require(vhost!=null && vhost.startsWith("b6b-"),"OWNED_VHOST_REQUIRED");
    HostedRunner.require(n.path("exchange").asText().equals("hotel.events")
      && java.util.Set.of("payment.completed","payment.refunded").contains(n.path("routingKey").asText()),"OWNED_REPLAY_ROUTE");
    ConnectionFactory f=new ConnectionFactory();f.setHost("127.0.0.1");f.setPort(Integer.parseInt(System.getenv("SUPABASE_DEV_RABBIT_PORT")));
    f.setVirtualHost(vhost);f.setUsername(System.getenv("SUPABASE_DEV_RABBIT_USERNAME"));f.setPassword(System.getenv("SUPABASE_DEV_RABBIT_PASSWORD"));
    try(var connection=f.newConnection();var channel=connection.createChannel()) {
      channel.confirmSelect();
      var properties=new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).messageId(n.path("payload").path("eventId").asText()).build();
      for(int i=0;i<2;i++)channel.basicPublish("hotel.events",n.path("routingKey").asText(),true,properties,n.path("payload").toString().getBytes(StandardCharsets.UTF_8));
      channel.waitForConfirmsOrDie(10000);
    }
    System.out.println("OWNED_DUPLICATE_PAYMENT_EVENT_REPLAY_CONFIRMED");
  }
}
