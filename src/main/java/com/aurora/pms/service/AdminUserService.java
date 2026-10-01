package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateUserRequest;
import com.aurora.pms.dto.request.UpdateUserRequest;
import com.aurora.pms.dto.response.RoleResponse;
import com.aurora.pms.dto.response.UserAdminResponse;

public interface AdminUserService {

	List<UserAdminResponse> findUsers();

	UserAdminResponse findUser(UUID id);

	UserAdminResponse createUser(CreateUserRequest request);

	UserAdminResponse updateUser(UUID id, UpdateUserRequest request);

	List<RoleResponse> findRoles();
}
