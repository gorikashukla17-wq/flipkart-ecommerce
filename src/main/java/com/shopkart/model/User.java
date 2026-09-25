package com.shopkart.model;

/** A registered customer. The password hash never leaves the data layer. */
public record User(long userId, String email, String fullName) {
}
