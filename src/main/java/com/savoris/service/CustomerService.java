package com.savoris.service;

import com.savoris.dto.request.CreateCustomerRequest;
import com.savoris.dto.response.CustomerResponse;

import java.util.List;

public interface CustomerService {
    CustomerResponse createCustomer(CreateCustomerRequest request);
    CustomerResponse getCustomer(Integer customerId);
    CustomerResponse updateCustomer(Integer customerId, CreateCustomerRequest request);
    void deleteCustomer(Integer customerId);
    List<CustomerResponse> searchCustomers(String fullName);
    CustomerResponse findByPhone(String phone);
}
