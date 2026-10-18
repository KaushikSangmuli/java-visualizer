package com.example.demo.model;

/** Source location and reason for an API rejected by the defense-in-depth policy. */
public record BlockedApi(int line, String api, String reason) {}
