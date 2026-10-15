package com.savoris.security;

/**
 * Who may call what, used with @PreAuthorize. Branch-level checks (staff only touch their own branch)
 * are done on top of this in the services via BranchAccessGuard.
 */
public final class Roles {

    // Chain owner only: branches, chain menu, chain dashboard
    public static final String ADMIN = "hasRole('ADMIN')";
    // Owner and branch managers: staff, refunds, branch settings
    public static final String MANAGEMENT = "hasAnyRole('ADMIN','BRANCH_MANAGER')";
    // Creating front-of-house orders
    public static final String ORDER_WRITE = "hasAnyRole('ADMIN','BRANCH_MANAGER','WAITER')";
    public static final String FRONT_OF_HOUSE = ORDER_WRITE;
    // Handling money and closing orders
    public static final String PAYMENT = FRONT_OF_HOUSE;
    // Everyone in the restaurant permission matrix: admin, manager, front of house, chef
    public static final String RESTAURANT = "hasAnyRole('ADMIN','BRANCH_MANAGER','WAITER','CHEF')";
    public static final String SERVICE = RESTAURANT;
    // Cooks and owner may move dishes through the kitchen flow
    public static final String KITCHEN = "hasAnyRole('ADMIN','CHEF')";
    // Warehouse staff handle stock at their own branch; branch scope is enforced in services.
    public static final String STOCK = "hasAnyRole('ADMIN','BRANCH_MANAGER','WAREHOUSE')";
    public static final String STOCK_READ = "hasAnyRole('ADMIN','BRANCH_MANAGER','WAREHOUSE')";

    private Roles() {
    }
}
