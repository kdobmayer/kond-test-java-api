package com.kond.orders.service;

import com.kond.orders.dto.CustomerRequest;
import com.kond.orders.dto.CustomerResponse;
import com.kond.orders.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void createAndGetCustomer() {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Alice");
        request.setLastName("Wonder");
        request.setEmail("alice@example.com");
        request.setCity("Portland");

        CustomerResponse created = customerService.createCustomer(request);
        assertNotNull(created.getId());
        assertEquals("Alice", created.getFirstName());

        CustomerResponse fetched = customerService.getCustomer(created.getId());
        assertEquals("alice@example.com", fetched.getEmail());
        assertEquals("Portland", fetched.getCity());
    }

    @Test
    void getAllCustomers() {
        CustomerRequest req1 = new CustomerRequest();
        req1.setFirstName("A");
        req1.setLastName("B");
        req1.setEmail("a@b.com");
        customerService.createCustomer(req1);

        CustomerRequest req2 = new CustomerRequest();
        req2.setFirstName("C");
        req2.setLastName("D");
        req2.setEmail("c@d.com");
        customerService.createCustomer(req2);

        List<CustomerResponse> all = customerService.getAllCustomers();
        assertEquals(2, all.size());
    }

    @Test
    void getCustomer_notFound_throws() {
        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomer(999L));
    }

    @Test
    void deleteCustomer_notFound_throws() {
        assertThrows(ResourceNotFoundException.class, () -> customerService.deleteCustomer(999L));
    }

    @Test
    void duplicateEmail_throws() {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("X");
        request.setLastName("Y");
        request.setEmail("dup@test.com");
        customerService.createCustomer(request);

        assertThrows(IllegalArgumentException.class, () -> customerService.createCustomer(request));
    }
}
