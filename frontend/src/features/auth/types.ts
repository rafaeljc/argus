export interface LoginBody {
  email: string;
  password: string;
}

export interface SessionResult {
  user_id: string;
  expires_at: string;
}
