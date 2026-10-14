package com.savoris.dto.request;

import com.savoris.constant.VoidResolution;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoidResolveRequest {
    @NotNull
    private VoidResolution resolution;
}
