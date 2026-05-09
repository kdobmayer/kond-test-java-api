# kond-test-java-api

Order Management API — Spring Boot 3.4 + JPA + H2.

## Build & Test

```bash
mvn compile
mvn test
```

## Features

- Orders: create, update status, cancel, list with state machine (CREATED → CONFIRMED → SHIPPED → DELIVERED, CANCELLED)
- Customers: full CRUD
- Products: CRUD + stock management
- Inventory: reserve, release, check availability
- Shipping: calculate rates, create shipment, track

## Known Issues

- No optimistic locking on inventory (race condition under concurrent access)
- Duplicated validation logic between Orders and Inventory controllers
- Shipping rate calculator has no test coverage
