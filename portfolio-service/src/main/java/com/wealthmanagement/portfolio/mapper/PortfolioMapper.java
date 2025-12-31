package com.wealthmanagement.portfolio.mapper;

import com.wealthmanagement.portfolio.dto.HoldingDTO;
import com.wealthmanagement.portfolio.dto.PortfolioDTO;
import com.wealthmanagement.portfolio.model.Holding;
import com.wealthmanagement.portfolio.model.Portfolio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "holdings", ignore = true)
    @Mapping(target = "totalValue", constant = "0")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Portfolio toEntity(PortfolioDTO.CreateRequest request);

    PortfolioDTO.Response toResponse(Portfolio portfolio);

    PortfolioDTO.Summary toSummary(Portfolio portfolio);

    List<PortfolioDTO.Response> toResponseList(List<Portfolio> portfolios);

    @Mapping(target = "marketValue", ignore = true)
    @Mapping(target = "unrealizedGainLoss", ignore = true)
    @Mapping(target = "unrealizedGainLossPercentage", ignore = true)
    @Mapping(target = "lastUpdated", expression = "java(java.time.LocalDateTime.now())")
    Holding toHoldingEntity(HoldingDTO.AddRequest request);

    HoldingDTO.Response toHoldingResponse(Holding holding);

    List<HoldingDTO.Response> toHoldingResponseList(List<Holding> holdings);

    void updateEntityFromRequest(PortfolioDTO.UpdateRequest request, @MappingTarget Portfolio portfolio);

    @Mapping(target = "lastUpdated", expression = "java(java.time.LocalDateTime.now())")
    void updateHoldingFromRequest(HoldingDTO.UpdateRequest request, @MappingTarget Holding holding);

    default Integer getHoldingsCount(Portfolio portfolio) {
        return portfolio.getHoldings() != null ? portfolio.getHoldings().size() : 0;
    }
}
