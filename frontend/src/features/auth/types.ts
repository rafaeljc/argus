export interface LoginBody {
  email: string;
  password: string;
}

export interface SessionResult {
  user_id: string;
  expires_at: string;
}

export interface SignupBody {
  email: string;
  password: string;
}

export interface SignupResult {
  user_id: string;
  verification_sent: boolean;
}
