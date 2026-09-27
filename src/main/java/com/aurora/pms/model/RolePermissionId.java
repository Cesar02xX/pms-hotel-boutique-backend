package com.aurora.pms.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class RolePermissionId implements Serializable {

	@Column(name = "role_id", nullable = false)
	private UUID roleId;

	@Column(name = "permission_id", nullable = false)
	private UUID permissionId;
}
