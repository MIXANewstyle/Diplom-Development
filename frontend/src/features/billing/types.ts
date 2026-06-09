export type SubStatus = 'ACTIVE' | 'CANCELED' | 'EXPIRED';
export type SubTier = 'BASIC' | 'PREMIUM';
export type TxnType = 'PURCHASE' | 'RENEWAL' | 'TRIAL' | 'REFUND';
export type TxnStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

export interface Plan {
  id: number;
  code: string;
  tier: string;
  durationDays: number;
  price: number;
  currency: string;
}

export interface Subscription {
  tier: string;
  status: string;
  startedAt: string;
  expiresAt: string;
  trialUsed: boolean;
}

export interface Transaction {
  id: string;
  type: string;
  status: string;
  planCode: string;
  amount: number;
  currency: string;
  createdAt: string;
}

export interface CheckoutBody {
  planId: number;
  promoCode?: string;
}

export interface CheckoutResponse {
  transactionId: string;
  status: string;
  amount: number;
  currency: string;
  confirmationUrl?: string;
}

export interface PromoValidationBody {
  planId: number;
  code: string;
}

export interface PromoValidationResponse {
  valid: boolean;
  discountType: string;
  discountAmount: number;
  finalAmount: number;
}
