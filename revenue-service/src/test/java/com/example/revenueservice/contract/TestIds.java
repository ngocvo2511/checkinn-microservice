package com.example.revenueservice.contract;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
/** Stable valid domain fixtures only. Never used for provider IDs or production hash recipes. */
public final class TestIds {
 private TestIds() {}
 public static UUID id(String alias) {
  if (alias == null) return null;
  if (alias.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) return UUID.fromString(alias);
  return UUID.nameUUIDFromBytes(("batch5a-fixture:"+alias).getBytes(StandardCharsets.UTF_8));
 }
 public static String wire(String alias) { return id(alias).toString(); }
 public static String text(UUID value) { return value == null ? null : value.toString(); }
}
