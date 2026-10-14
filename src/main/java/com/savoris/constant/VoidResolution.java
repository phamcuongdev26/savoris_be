package com.savoris.constant;

public enum VoidResolution {
    /** Chef accepts: item cancelled, ingredients restocked, removed from bill */
    ACCEPT,
    /** Chef remakes for free: current item cancelled, new PENDING item created at same price */
    REMAKE,
    /** Customer still pays: item moves back to READY (it was already cooked) */
    CHARGE
}
