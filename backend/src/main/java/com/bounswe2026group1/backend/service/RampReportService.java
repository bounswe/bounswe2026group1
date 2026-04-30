package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateRampReportRequest;
import com.bounswe2026group1.backend.dto.RampReportResponse;
import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RampReportRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RampReportService {

    private final RampReportRepository rampReportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final OverpassService overpassService;

    public RampReportResponse create(CreateRampReportRequest request) {
        RegisteredUser user = registeredUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        Location reportedPoint = new Location(request.getLatitude(), request.getLongitude());

        // Snap to nearest OSM stair — orientation resolved at routing time
        Location[] endpoints;
        try {
            endpoints = overpassService.snapToNearestStair(reportedPoint);
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            throw new RoutingException(HttpStatus.BAD_REQUEST,
                    "Could not verify stair location. Please try again later.");
        }

        RampReport report = new RampReport(user, reportedPoint, request.getDescription(),
                endpoints[0], endpoints[1]);
        return RampReportResponse.fromEntity(rampReportRepository.save(report));
    }

    public List<RampReportResponse> getAll() {
        return rampReportRepository.findAll().stream()
                .map(RampReportResponse::fromEntity)
                .toList();
    }

    public Optional<RampReportResponse> getById(Long id) {
        return rampReportRepository.findById(id)
                .map(RampReportResponse::fromEntity);
    }
}
