package com.ifi.aws.shortener.config;

import java.io.Serializable;

public record ApiClientPrincipal(String name, String tenantId, boolean isMasterAdmin) implements Serializable {
	public boolean isTenantBound() {
		return !isMasterAdmin && tenantId != null && !tenantId.isBlank();
	}
}
