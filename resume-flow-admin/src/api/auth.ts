import request from '@/utils/request';

export interface LoginData {
  username: string;
  password: string;
}

export interface RegisterData extends LoginData {
  email?: string;
  phone?: string;
}

export const authApi = {
  login: (data: LoginData) =>
    request.post<any, { token: string; userId: number; username: string }>('/auth/login', data),

  register: (data: RegisterData) => request.post('/auth/register', data),

  logout: () => request.post('/auth/logout'),

  getMe: () =>
    request.get<any, { id: number; username: string; email: string; phone: string }>('/auth/me'),

  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put('/auth/password', data),
};
