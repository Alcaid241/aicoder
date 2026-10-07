import request from './request'
import type { UserDTO, RoleDTO, MenuDTO, MenuTreeNode, PageResult } from '@/types'

export const systemApi = {
  // 用户管理
  userList: (page: number, size: number, keyword?: string) =>
    request.get<PageResult<UserDTO>>('/system/user/list', { params: { page, size, keyword } }),
  userGet: (id: number) => request.get<UserDTO>(`/system/user/${id}`),
  userCreate: (data: { username: string; password: string; nickname: string; email?: string }) =>
    request.post<UserDTO>('/system/user', data),
  userUpdate: (id: number, data: Partial<UserDTO>) =>
    request.put<UserDTO>(`/system/user/${id}`, data),
  userDelete: (id: number) => request.delete(`/system/user/${id}`),
  userToggleStatus: (id: number) => request.put(`/system/user/${id}/status`),
  userAssignRoles: (id: number, roleIds: number[]) =>
    request.put(`/system/user/${id}/roles`, { roleIds }),
  userResetPassword: (id: number, newPassword: string) =>
    request.put(`/system/user/${id}/reset-password`, newPassword),

  // 角色管理
  roleList: () => request.get<RoleDTO[]>('/system/role/list'),
  roleGet: (id: number) => request.get<RoleDTO>(`/system/role/${id}`),
  roleCreate: (data: { name: string; code: string; sort?: number; remark?: string }) =>
    request.post<RoleDTO>('/system/role', data),
  roleUpdate: (id: number, data: { name?: string; sort?: number; remark?: string }) =>
    request.put<RoleDTO>(`/system/role/${id}`, data),
  roleDelete: (id: number) => request.delete(`/system/role/${id}`),
  roleMenuIds: (id: number) => request.get<number[]>(`/system/role/${id}/menus`),
  roleAssignMenus: (id: number, menuIds: number[]) =>
    request.put(`/system/role/${id}/menus`, { menuIds }),

  // 菜单管理
  menuTree: () => request.get<MenuTreeNode[]>('/system/menu/tree'),
  menuUserTree: () => request.get<MenuTreeNode[]>('/system/menu/user'),
  menuCreate: (data: { parentId?: number | null; name: string; path?: string; icon?: string; sort?: number; type: number }) =>
    request.post<MenuDTO>('/system/menu', data),
  menuUpdate: (id: number, data: Partial<MenuDTO>) =>
    request.put<MenuDTO>(`/system/menu/${id}`, data),
  menuDelete: (id: number) => request.delete(`/system/menu/${id}`),

  // 权限管理
  permissionRoles: () => request.get<Array<{ role: RoleDTO; menuIds: number[] }>>('/system/permission/roles'),
  permissionMenuTree: () => request.get<MenuTreeNode[]>('/system/permission/menu-tree'),
  permissionAssign: (roleId: number, menuIds: number[]) =>
    request.put(`/system/permission/role/${roleId}/menus`, { menuIds }),
}
