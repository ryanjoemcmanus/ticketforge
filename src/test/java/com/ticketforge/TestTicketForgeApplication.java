package com.ticketforge;

import org.springframework.boot.SpringApplication;

public class TestTicketForgeApplication {

  public static void main(String[] args) {
    SpringApplication.from(TicketForgeApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
