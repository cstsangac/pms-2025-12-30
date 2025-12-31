package com.wealthmanagement.transaction.mapper;

import com.wealthmanagement.transaction.dto.TransactionDTO;
import com.wealthmanagement.transaction.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "transactionDate", ignore = true)
    @Mapping(target = "processedDate", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    Transaction toEntity(TransactionDTO.CreateRequest request);

    TransactionDTO.Response toResponse(Transaction transaction);

    List<TransactionDTO.Response> toResponseList(List<Transaction> transactions);
}
