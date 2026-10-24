package com.savoris.service;

import com.savoris.dto.request.AssignShiftRequest;
import com.savoris.dto.request.CreateShiftRequest;
import com.savoris.dto.response.ShiftResponse;
import com.savoris.dto.response.StaffShiftResponse;
import com.savoris.dto.response.TimesheetResponse;

import java.time.LocalDate;
import java.util.List;

public interface ShiftService {
    ShiftResponse createShift(Integer actorId, Integer branchId, CreateShiftRequest request);
    List<ShiftResponse> getShifts(Integer actorId, Integer branchId);
    StaffShiftResponse assign(Integer actorId, Integer shiftId, AssignShiftRequest request);
    void unassign(Integer actorId, Integer assignmentId);
    List<StaffShiftResponse> getSchedule(Integer actorId, Integer branchId, LocalDate from, LocalDate to);
    List<StaffShiftResponse> getMySchedule(Integer actorId, LocalDate from, LocalDate to);
    StaffShiftResponse checkIn(Integer actorId, Integer assignmentId);
    StaffShiftResponse checkOut(Integer actorId, Integer assignmentId);
    List<TimesheetResponse> getTimesheet(Integer actorId, Integer branchId, LocalDate from, LocalDate to);
    TimesheetResponse getMyTimesheet(Integer actorId, LocalDate from, LocalDate to);
}
