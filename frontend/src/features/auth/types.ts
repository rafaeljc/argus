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

export interface VerifyEmailBody {
  token: string;
}

export interface PasswordResetRequestBody {
  email: string;
}

export interface PasswordResetConfirmBody {
  token: string;
  new_password: string;
}
