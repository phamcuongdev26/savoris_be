package com.savoris.dto.response;

/**
 * A branch as guests choosing where to pick up takeaway see it. acceptingTakeaway is false while its kitchen is full;
 * transferAvailable is false until the owner set the bank QR image.
 */
public record PublicBranchResponse(Integer id, String code, String name, String address, boolean acceptingTakeaway,
                                   Integer estimatedWaitMinutes, boolean transferAvailable, String city, Double latitude,
                                   Double longitude, boolean openNow, String phone, String email, java.util.List<Hours> hours) {
    public record Hours(String days, String time) {}
}
